package top.bogey.touch_tool.ui.blueprint.card;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import top.bogey.touch_tool.bean.other.run.ActionRunResult;
import top.bogey.touch_tool.bean.other.run.RunResultSaver;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionInfo;
import top.bogey.touch_tool.bean.action.ActionListener;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.ui.blueprint.CardLayoutView;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.DisplayUtil;

public abstract class ActionCard extends MaterialCardView implements ActionListener {
    protected final Task task;
    protected final Action action;

    protected final Map<String, PinView> pinViews = new HashMap<>();
    private boolean needDelete = false;
    private boolean needDraw = true;

    private MaterialTextView posView;
    private LinearLayout runBadgeBox;
    private MaterialButton runBadgeButton;
     public ActionCard(Context context, Task task, Action action) {
        super(context);
        this.task = task;
        this.action = action;

        setCardBackgroundColor(DisplayUtil.getAttrColor(context, com.google.android.material.R.attr.colorSurfaceVariant));
        setStrokeColor(DisplayUtil.getAttrColor(context, com.google.android.material.R.attr.colorPrimaryVariant));
        setStrokeWidth(1);
        setElevation(8);
        setPivotX(0);
        setPivotY(0);

        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        setLayoutParams(params);

        init();

        action.getPins().forEach(this::addPinView);
        action.addListener(this);
        bringRunBadgeToFront();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        action.removeListener(this);
    }

    public abstract void init();

    public abstract void refreshCardInfo();

    public abstract void refreshCardLockState();

    protected void initCardInfo(ShapeableImageView icon, MaterialTextView title, MaterialTextView des) {
        if (icon != null) {
            ActionInfo info = ActionInfo.getActionInfo(action.getType());
            if (info != null) icon.setImageResource(info.getIcon());
        }

        if (title != null) title.setText(action.getTitle());

        if (des != null) {
            des.setText(action.getDescription());
            des.setVisibility((action.getDescription() == null || action.getDescription().isEmpty()) ? GONE : VISIBLE);
        }

        initRunBadge();
        initDebugButton();
    }

    // 区间调试按钮：插入按钮组最前（icon_refresh），菜单含"从此开始/重跑到此"
    private void initDebugButton() {
        ViewGroup topBox = findViewById(R.id.topBox);
        if (topBox == null) return;
        ViewGroup buttonGroup = null;
        for (int i = 0; i < topBox.getChildCount(); i++) {
            if (topBox.getChildAt(i) instanceof com.google.android.material.button.MaterialButtonGroup) {
                buttonGroup = (ViewGroup) topBox.getChildAt(i);
                break;
            }
        }
        // 防重复：清掉旧调试按钮
        for (int i = getChildCount() - 1; i >= 0; i--) {
            if ("debug_button".equals(getChildAt(i).getTag())) removeViewAt(i);
        }

        if (buttonGroup == null) {
            // 兜底：无按钮组的卡片，调试按钮挂左上角覆盖层
            com.google.android.material.button.MaterialButton overlayButton =
                    new com.google.android.material.button.MaterialButton(getContext(), null, R.attr.iconButton);
            overlayButton.setIconResource(R.drawable.icon_refresh);
            overlayButton.setTag("debug_button");
            overlayButton.setOnClickListener(v -> runDebug());
            int margin = (int) DisplayUtil.dp2px(getContext(), 2);
            LayoutParams overlayParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            overlayParams.setMargins(margin, margin, 0, 0);
            overlayParams.gravity = Gravity.TOP | Gravity.START;
            addView(overlayButton, overlayParams);
            return;
        }

        for (int i = buttonGroup.getChildCount() - 1; i >= 0; i--) {
            if ("debug_button".equals(buttonGroup.getChildAt(i).getTag())) buttonGroup.removeViewAt(i);
        }

        com.google.android.material.button.MaterialButton debugButton =
                new com.google.android.material.button.MaterialButton(getContext(), null, R.attr.iconButton);
        debugButton.setIconResource(R.drawable.icon_refresh);
        debugButton.setTag("debug_button");
        debugButton.setOnClickListener(v -> runDebug());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_VERTICAL;
        buttonGroup.addView(debugButton, 0, params);
        // 按钮组后加的视图 z 序更高，重新置顶防被盖
        debugButton.post(() -> debugButton.bringToFront());
    }

    // 从当前动作开始执行到任务自然结束
    private void runDebug() {
        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null) {
            Toast.makeText(getContext(), R.string.debug_run_need_service, Toast.LENGTH_SHORT).show();
            return;
        }

        Pin entry = findEntryPin(action);
        if (entry == null) {
            Toast.makeText(getContext(), R.string.debug_run_no_link, Toast.LENGTH_SHORT).show();
            return;
        }
        TaskRunnable runnable = service.runTask(task, new top.bogey.touch_tool.bean.action.start.InnerStartAction(entry), null);
        startFocusAnim();
    }

    private Pin findEntryPin(Action target) {
        for (Pin pin : target.getPins()) {
            if (!pin.isOut() && pin.getLinkedPin(task) != null) return pin;
        }
        return null;
    }

    // 运行结果角标：MaterialButton（与标题行铅笔按钮同款，保证点击行为一致），绿✓=成功 红ⓘ=未达成 红✕=异常，左侧显示耗时
    private void initRunBadge() {
        runBadgeBox = new LinearLayout(getContext());
        LinearLayout badgeBox = runBadgeBox;
        badgeBox.setOrientation(LinearLayout.HORIZONTAL);
        badgeBox.setGravity(Gravity.CENTER_VERTICAL);
        badgeBox.setTag("run_badge");

        runBadgeButton = new MaterialButton(getContext(), null, R.attr.iconButton);
        MaterialButton badge = runBadgeButton;
        badge.setTextSize(10);
        badge.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        badge.setIconSize((int) DisplayUtil.dp2px(getContext(), 14));
        badge.setMinWidth(0);
        badge.setInsetTop(0);
        badge.setInsetBottom(0);
        badgeBox.addView(badge);

        badgeBox.setVisibility(GONE);

        // 防重复：清掉旧角标
        for (int i = getChildCount() - 1; i >= 0; i--) {
            if ("run_badge".equals(getChildAt(i).getTag())) removeViewAt(i);
        }

        // 右上角覆盖层（铅笔按钮右侧的空白角，与已验证可点击的左上角方案同机制）
        int margin = (int) DisplayUtil.dp2px(getContext(), 2);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, margin, margin, 0);
        params.gravity = Gravity.TOP | Gravity.END;
        addView(badgeBox, params);
        runBadgeBox = badgeBox;
        // 针脚槽在角标之后添加（z序更高会盖住角标），延迟提到最上层
        badgeBox.post(() -> badgeBox.bringToFront());

        badge.setOnClickListener(v -> showRunResultDialog());
        refreshRunBadge();
    }

    // 重新读取运行结果并更新角标显示（结果落库时由画布通知刷新）
    // 残留检测：结果代号与任务当前运行代号不一致 = 本次运行未执行该动作，显示灰色
    public void refreshRunBadge() {
        if (runBadgeBox == null || runBadgeButton == null) return;
        if (task.hasFlag(Task.FLAG_HIDE_RUN_BADGE)) {
            runBadgeBox.setVisibility(GONE);
            return;
        }
        ActionRunResult result = RunResultSaver.getInstance().get(action.getId());
        if (result == null) {
            runBadgeBox.setVisibility(GONE);
            return;
        }
        runBadgeBox.setVisibility(VISIBLE);
        boolean stale = result.timestamp < RunResultSaver.getInstance().getRunBeginTime(task.getId());
        if (stale) {
            runBadgeButton.setText("");
            runBadgeButton.setIconResource(R.drawable.icon_visibility_off);
            runBadgeButton.setIconTint(android.content.res.ColorStateList.valueOf(0xFF888780));
            return;
        }
        runBadgeButton.setText(" " + formatDuration(result.duration));
        switch (result.status) {
            case ActionRunResult.STATUS_SUCCESS -> {
                runBadgeButton.setIconResource(R.drawable.icon_check_circle);
                runBadgeButton.setIconTint(android.content.res.ColorStateList.valueOf(0xFF2E7D32));
            }
            case ActionRunResult.STATUS_UNACHIEVED -> {
                runBadgeButton.setIconResource(R.drawable.icon_info);
                runBadgeButton.setIconTint(android.content.res.ColorStateList.valueOf(0xFFE24B4A));
            }
            default -> {
                runBadgeButton.setIconResource(R.drawable.icon_cancel);
                runBadgeButton.setIconTint(android.content.res.ColorStateList.valueOf(0xFFE24B4A));
            }
        }
    }

    private String formatDuration(long ms) {
        if (ms >= 1000) return String.format(java.util.Locale.ROOT, "%.1fs", ms / 1000.0);
        return ms + "ms";
    }

    public void showRunResultDialog() {
        ActionRunResult result = RunResultSaver.getInstance().get(action.getId());
        if (result == null) {
            Toast.makeText(getContext(), R.string.run_result_none, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder message = new StringBuilder();
        if (result.timestamp < RunResultSaver.getInstance().getRunBeginTime(task.getId())) {
            message.append(getContext().getString(R.string.run_result_stale)).append("\n\n");
        }
        message.append(getContext().getString(switch (result.status) {
            case ActionRunResult.STATUS_SUCCESS -> R.string.run_result_success;
            case ActionRunResult.STATUS_UNACHIEVED -> R.string.run_result_unachieved;
            default -> R.string.run_result_failed;
        }));
        message.append(" · ").append(getContext().getString(R.string.run_result_duration)).append(" ").append(formatDuration(result.duration));
        if (!result.outputs.isEmpty()) {
            message.append("\n\n");
            for (String[] output : result.outputs) {
                message.append(output[0]).append(": ").append(output[1]).append("\n");
            }
        } else {
            message.append("\n\n").append(getContext().getString(R.string.run_result_no_output));
        }

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) DisplayUtil.dp2px(getContext(), 20);
        layout.setPadding(pad, pad, pad, 0);

        TextView messageView = new TextView(getContext());
        messageView.setText(message.toString());
        messageView.setTextSize(13);
        layout.addView(messageView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 失败截图缩略图
        if (result.screenshotPath != null && new File(result.screenshotPath).exists()) {
            TextView shotHint = new TextView(getContext());
            shotHint.setText(R.string.run_result_screenshot);
            shotHint.setTextSize(12);
            shotHint.setPadding(0, (int) DisplayUtil.dp2px(getContext(), 10), 0, 4);
            layout.addView(shotHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            ImageView shotView = new ImageView(getContext());
            shotView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(result.screenshotPath, options);
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1024) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeFile(result.screenshotPath, options);
            if (bitmap != null) shotView.setImageBitmap(bitmap);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) DisplayUtil.dp2px(getContext(), 240));
            imageParams.topMargin = (int) DisplayUtil.dp2px(getContext(), 4);
            layout.addView(shotView, imageParams);
        }

        new MaterialAlertDialogBuilder(getContext())
                .setTitle(action.getTitle())
                .setView(layout)
                .setPositiveButton(R.string.cancel, null)
                .show();
    }

    protected void initEditDesc(MaterialButton button, MaterialTextView des) {
        button.setOnClickListener(v -> AppUtil.showEditDialog(getContext(), R.string.action_add_des, action.getDescription(), result -> {
            action.setDescription(result);
            des.setText(result);
            des.setVisibility((result == null || result.isEmpty()) ? GONE : VISIBLE);
        }));
    }

    protected void initDelete(MaterialButton button) {
        button.setOnClickListener(v -> {
            if (needDelete) {
                ((CardLayoutView) getParent()).removeCard(this);
            } else {
                button.setChecked(true);
                needDelete = true;
                postDelayed(() -> {
                    button.setChecked(false);
                    needDelete = false;
                }, 1500);
            }
        });
    }

    protected void initCopy(MaterialButton button) {
        button.setOnClickListener(v -> {
            Action copy = action.newCopy();
            copy.setUid(UUID.randomUUID().toString());
            ((CardLayoutView) getParent()).addCard(copy);
        });
    }

    protected void initLock(MaterialButton button) {
        button.setIconResource(action.isLocked() ? R.drawable.icon_lock : R.drawable.icon_lock_open);
        button.setChecked(action.isLocked());

        button.setOnClickListener(v -> {
            action.setLocked(!action.isLocked());
            button.setIconResource(action.isLocked() ? R.drawable.icon_lock : R.drawable.icon_lock_open);
            button.setChecked(action.isLocked());
        });
    }

    protected void initExpand(MaterialButton button) {
        setExpandType(action.getExpandType());
        refreshExpandButton(button);
        button.setOnClickListener(v -> {
            expand();
            refreshExpandButton(button);
        });
    }

    // 翻页按钮状态：第2页时高亮选中，一眼区分当前页
    private void refreshExpandButton(MaterialButton button) {
        button.setIconResource(switch (action.getExpandType()) {
            case NONE -> R.drawable.icon_visibility_off;
            case HALF -> R.drawable.icon_symptoms;
            case FULL -> R.drawable.icon_visibility;
        });
        button.setChecked(action.getExpandType() == Action.ExpandType.FULL);
    }

    protected void initPosView(MaterialTextView posView) {
        this.posView = posView;
    }

    public abstract boolean check();

    public void addPin(Pin pin) {
        action.addPin(pin);
    }

    public void addPin(Pin flag, Pin pin) {
        action.addPin(flag, pin);
    }

    public void addPinView(Pin pin) {
        addPinView(pin, 0);
    }

    /**
     * @param offset 添加到列表中的位置
     */
    public void addPin(Pin pin, int offset) {
        action.addPin(action.getPins().size() - offset, pin);
    }

    /**
     * @param offset 添加到上下左右各区域得偏移，不是添加到列表中的位置
     */
    public abstract void addPinView(Pin pin, int offset);

    public void removePin(Pin pin) {
        action.removePin(task, pin);
    }

    public void removePinView(Pin pin) {
        PinView view = pinViews.remove(pin.getId());
        if (view != null) ((ViewGroup) view.getParent()).removeView(view);
    }

    @SuppressLint("SetTextI18n")
    public void updateCardPos(float x, float y) {
        setX(x);
        setY(y);
        if (posView != null) posView.setText(action.getPos().x + "," + action.getPos().y);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 触摸点落在运行角标上时强制优先分发给角标，防止被针脚槽触控区抢走
        if (runBadgeBox != null && runBadgeBox.getVisibility() == VISIBLE && runBadgeBox.getParent() == this) {
            android.graphics.Rect hitRect = new android.graphics.Rect();
            runBadgeBox.getHitRect(hitRect);
            if (hitRect.contains((int) ev.getX(), (int) ev.getY())) {
                return runBadgeBox.dispatchTouchEvent(ev);
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!needDraw) return;
        super.draw(canvas);
    }

    public void startFocusAnim() {
        AlphaAnimation animation = new AlphaAnimation(1f, 0.5f);
        animation.setDuration(200);
        animation.setRepeatCount(3);
        animation.setRepeatMode(Animation.REVERSE);
        startAnimation(animation);
    }

    public void setSelected(boolean selected) {
        if (selected) {
            setStrokeWidth((int) DisplayUtil.dp2px(getContext(), 1));
            setCardBackgroundColor(DisplayUtil.getAttrColor(getContext(), com.google.android.material.R.attr.colorSecondaryContainer));
        } else {
            setStrokeWidth(1);
            setCardBackgroundColor(DisplayUtil.getAttrColor(getContext(), com.google.android.material.R.attr.colorSurfaceVariant));
        }
    }

    public void setDescription(String description) {
        action.setDescription(description);
    }

    public void setExpandType(Action.ExpandType expandType) {
        if (expandType == Action.ExpandType.HALF && !action.canExpand()) {
            expandType = Action.ExpandType.FULL;
        }
        action.setExpandType(expandType);
        pinViews.forEach((id, pinView) -> pinView.expand(action.getExpandType()));
    }

    public void expand() {
        Action.ExpandType expandType = action.getExpandType();
        if (action.canExpand()) {
            // 有隐藏针脚的卡片：两页切换（第1页基本=HALF，第2页高级=FULL）
            action.setExpandType(expandType == Action.ExpandType.FULL ? Action.ExpandType.HALF : Action.ExpandType.FULL);
        } else {
            switch (expandType) {
                case NONE -> action.setExpandType(Action.ExpandType.FULL);
                case HALF, FULL -> action.setExpandType(Action.ExpandType.NONE);
            }
        }
        pinViews.forEach((id, pinView) -> pinView.expand(action.getExpandType()));
    }

    public PinView getPinView(String pinId) {
        return pinViews.get(pinId);
    }

    public PinView getLinkAblePinView(float x, float y) {
        float scale = getScaleX();

        for (Map.Entry<String, PinView> entry : pinViews.entrySet()) {
            PinView pinView = entry.getValue();
            if (pinView.getVisibility() != VISIBLE) continue;
            if (!pinView.getPin().linkAble()) continue;

            PointF pos = DisplayUtil.getLocationRelativeToView(pinView, this);
            float px = pos.x * scale;
            float py = pos.y * scale;
            float width = pinView.getWidth() * scale;
            float height = pinView.getHeight() * scale;

            if (pinView.getPin().isVertical()) {
                // 上下的针脚取24dp的高度
                float offset = DisplayUtil.dp2px(getContext(), 24 * scale);
                if (pinView.getPin().isOut()) py = py + height - offset;
                height = offset;
            } else {
                // 左右的针脚取32dp的宽度
                float offset = DisplayUtil.dp2px(getContext(), 32 * scale);
                if (pinView.getPin().isOut()) px = px + width - offset;
                width = offset;
            }

            if (new RectF(px, py, px + width, py + height).contains(x, y)) return pinView;
        }

        return null;
    }

    // 触摸点是否落在运行结果角标上（卡片相对坐标，与画布命中测试同一坐标系）
    public boolean isRunBadgeHit(float x, float y) {
        if (runBadgeBox == null || runBadgeBox.getVisibility() != VISIBLE || runBadgeBox.getWidth() <= 0) return false;
        float scale = getScaleX();
        float pad = DisplayUtil.dp2px(getContext(), 4);
        float bx = runBadgeBox.getX() * scale - pad;
        float by = runBadgeBox.getY() * scale - pad;
        float bw = runBadgeBox.getWidth() * scale + pad * 2;
        float bh = runBadgeBox.getHeight() * scale + pad * 2;
        return new RectF(bx, by, bx + bw, by + bh).contains(x, y);
    }

    public boolean isEmptyPosition(float x, float y) {
        float scale = getScaleX();

        if (isRunBadgeHit(x, y)) return false;

        for (Map.Entry<String, PinView> entry : pinViews.entrySet()) {
            PinView pinView = entry.getValue();
            PointF pointF = DisplayUtil.getLocationRelativeToView(pinView, this);
            float px = pointF.x * scale;
            float py = pointF.y * scale;
            float width = pinView.getWidth() * scale;
            float height = pinView.getHeight() * scale;
            if (new RectF(px, py, px + width, py + height).contains(x, y)) return false;
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 测量最大值
        int widthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        super.onMeasure(widthSpec, heightSpec);

        // 对齐网格宽度，高度不用
        float gridSize = DisplayUtil.dp2px(getContext(), CardLayoutView.GRID_DP_SIZE);
        int measureWidth = getMeasuredWidth();
        int measureHeight = getMeasuredHeight();
        int gridWidth = (int) (Math.ceil(measureWidth / gridSize) * gridSize);
        setMeasuredDimension(gridWidth, measureHeight);

        // 使用新的测量参数重新测量
        widthSpec = MeasureSpec.makeMeasureSpec(gridWidth, MeasureSpec.EXACTLY);
        heightSpec = MeasureSpec.makeMeasureSpec(measureHeight, MeasureSpec.EXACTLY);
        super.onMeasure(widthSpec, heightSpec);
    }

    @SuppressLint("WrongCall")
    protected void originOnMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    // 角标必须保持在卡片子视图的最顶层：针脚槽在角标之后加入（z序更高），
    // 右缘针脚槽的触控区会一直延伸到标题行高度，不置顶就会吞掉角标点击
    public void bringRunBadgeToFront() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            if ("run_badge".equals(getChildAt(i).getTag())) {
                getChildAt(i).bringToFront();
                return;
            }
        }
    }

    @Override
    public void onPinAdded(Pin pin, int index) {
        List<Pin> pins = new ArrayList<>();
        // 找到pin方向方位一致的所有pin
        for (Pin currPin : action.getPins()) {
            // 方向一致 且 方位一致
            if (pin.isOut() == currPin.isOut() && pin.isVertical() == currPin.isVertical()) {
                pins.add(currPin);
            }
        }
        int idx = pins.indexOf(pin);
        addPinView(pin, pins.size() - 1 - idx);
        bringRunBadgeToFront();
    }

    @Override
    public void onPinRemoved(Pin pin) {
        removePinView(pin);
    }

    @Override
    public void onPinChanged(Pin pin) {
        pinViews.forEach((id, pinView) -> pinView.expand(action.getExpandType()));
        check();
    }

    public void setNeedDraw(boolean needDraw) {
        this.needDraw = needDraw;
    }

    public boolean isNeedDraw() {
        return needDraw;
    }

    public Task getTask() {
        return task;
    }

    public Action getAction() {
        return action;
    }

    public Map<String, PinView> getPinViews() {
        return pinViews;
    }
}
