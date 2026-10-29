package top.bogey.touch_tool.ui.blueprint.card;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.button.MaterialButton;

import java.util.Arrays;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionCheckResult;
import top.bogey.touch_tool.bean.action.app.OpenUriSchemeAction;
import top.bogey.touch_tool.bean.action.start.TimeStartAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.databinding.CardNormalBinding;
import top.bogey.touch_tool.ui.blueprint.pin.PinBottomView;
import top.bogey.touch_tool.ui.blueprint.pin.PinLeftView;
import top.bogey.touch_tool.ui.blueprint.pin.PinRightView;
import top.bogey.touch_tool.ui.blueprint.pin.PinTopView;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.DisplayUtil;

@SuppressLint("ViewConstructor")
public class NormalActionCard extends ActionCard {
    private CardNormalBinding binding;

    public NormalActionCard(Context context, Task task, Action action) {
        super(context, task, action);
    }

    @Override
    public void init() {
        binding = CardNormalBinding.inflate(LayoutInflater.from(getContext()), this, true);

        initCardInfo(binding.icon, binding.title, binding.des);
        initEditDesc(binding.editButton, binding.des);
        initHelp(binding.helpButton);
        initDelete(binding.removeButton);
        initCopy(binding.copyButton);
        initLock(binding.lockButton);
        initExpand(binding.expandButton);
        initTest();
        initNextTime();
        initPosView(binding.position);
    }

    // 「定时执行」卡片专属：显示下次执行时间，排期一目了然
    private void initNextTime() {
        if (action instanceof TimeStartAction timeStartAction && timeStartAction.isEnable()) {
            long next = timeStartAction.getNextStartTime(System.currentTimeMillis());
            if (next > 0) {
                binding.nextTimeText.setText(getContext().getString(R.string.time_start_action_next, AppUtil.formatDateTime(getContext(), next, false, true)));
                binding.nextTimeText.setVisibility(VISIBLE);
            }
        }
    }

    // 「打开链接」卡片专属：试打开按钮，按当前配置直接打开链接，不用跑整个任务
    private void initTest() {
        if (action instanceof OpenUriSchemeAction schemeAction) {
            binding.testButton.setVisibility(VISIBLE);
            binding.testButton.setOnClickListener(v -> schemeAction.testRun());
        }
    }

    @Override
    public void refreshCardInfo() {
        initCardInfo(binding.icon, binding.title, binding.des);
    }

    @Override
    public void refreshCardLockState() {
        initLock(binding.lockButton);
    }

    @Override
    public boolean check() {
        ActionCheckResult result = new ActionCheckResult();
        action.check(result, task);
        ActionCheckResult.Result importantResult = result.getImportantResult();
        if (importantResult != null) {
            binding.errorText.setVisibility(VISIBLE);
            binding.errorText.setText(importantResult.msg());
            binding.errorText.setBackgroundColor(DisplayUtil.getAttrColor(getContext(), importantResult.type().getBackgroundColor()));
            binding.errorText.setTextColor(DisplayUtil.getAttrColor(getContext(), importantResult.type().getTextColor()));
        } else {
            binding.errorText.setVisibility(GONE);
        }
        return importantResult == null || importantResult.type() != ActionCheckResult.ResultType.ERROR;
    }

    @Override
    public void addPinView(Pin pin, int offset) {
        PinView pinView;
        if (pin.isOut()) {
            if (pin.isVertical()) {
                pinView = new PinBottomView(getContext(), this, pin);
                binding.bottomBox.addView(pinView, binding.bottomBox.getChildCount() - offset);
            } else {
                pinView = new PinRightView(getContext(), this, pin);
                binding.outBox.addView(pinView, binding.outBox.getChildCount() - offset);
            }
        } else {
            if (pin.isVertical()) {
                pinView = new PinTopView(getContext(), this, pin);
                binding.topBox.addView(pinView, binding.topBox.getChildCount() - offset);
            } else {
                pinView = new PinLeftView(getContext(), this, pin);
                binding.inBox.addView(pinView, binding.inBox.getChildCount() - offset);
            }
        }
        pinView.expand(action.getExpandType());
        pinViews.put(pin.getId(), pinView);
    }

    @Override
    public boolean isEmptyPosition(float x, float y) {
        float scale = getScaleX();

        List<View> buttons = Arrays.asList(binding.helpButton, binding.editButton, binding.testButton, binding.lockButton, binding.expandButton, binding.copyButton, binding.removeButton);
        for (View button : buttons) {
            PointF pointF = DisplayUtil.getLocationRelativeToView(button, this);
            float px = pointF.x * scale;
            float py = pointF.y * scale;
            float width = button.getWidth() * scale;
            float height = button.getHeight() * scale;
            if (new RectF(px, py, px + width, py + height).contains(x, y)) return false;
        }
        return super.isEmptyPosition(x, y);
    }
}
