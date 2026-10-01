package top.bogey.touch_tool.ui.blueprint.card;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.normal.ShowTextAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.databinding.CardTextBinding;
import top.bogey.touch_tool.ui.blueprint.CardLayoutView;
import top.bogey.touch_tool.utils.DisplayUtil;
import top.bogey.touch_tool.utils.listener.TextChangedListener;

@SuppressLint("ViewConstructor")
public class ShowTextActionCard extends ActionCard {
    private static final int MIN_SIZE = 10;

    private CardTextBinding binding;
    private float lastX, lastY;
    private int originalWidth, originalHeight;
    private boolean isResizing = false;
    private float gridSize;

    public ShowTextActionCard(Context context, Task task, Action action) {
        super(context, task, action);
        getBackground().setAlpha((int) (0.25f * 255));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void init() {
        // 初始化gridSize，与CardLayoutView中的gridSize保持一致（8dp）
        gridSize = DisplayUtil.dp2px(getContext(), CardLayoutView.GRID_DP_SIZE);
        binding = CardTextBinding.inflate(LayoutInflater.from(getContext()), this, true);

        initDelete(binding.removeButton);
        initCopy(binding.copyButton);
        initLock(binding.lockButton);

        binding.textEdit.addTextChangedListener(new TextChangedListener() {
            @Override
            public void afterTextChanged(Editable s) {
                action.setDescription(s.toString());
            }
        });
        binding.textEdit.setText(action.getDescription());

        // 如果action是ShowTextAction，设置初始尺寸
        if (action instanceof ShowTextAction showTextAction) {
            Point size = showTextAction.getSize();
            if (size.x > 0 && size.y > 0) {
                // 将相对网格大小转换为实际像素大小
                ViewGroup.LayoutParams params = binding.linearLayout.getLayoutParams();
                params.width = (int) (size.x * gridSize);
                params.height = (int) (size.y * gridSize);
                binding.linearLayout.setLayoutParams(params);
            }
        }

        binding.dragImage.setOnTouchListener((v, event) -> {
            float x = event.getRawX();
            float y = event.getRawY();
            float scale = getScaleX();

            x = x / scale;
            y = y / scale;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = x;
                    lastY = y;
                    // 保存原始尺寸
                    originalWidth = binding.linearLayout.getWidth();
                    originalHeight = binding.linearLayout.getHeight();
                    isResizing = true;
                    requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isResizing) {
                        float deltaX = x - lastX;
                        float deltaY = y - lastY;

                        float newWidth = originalWidth + deltaX;
                        float newHeight = originalHeight + deltaY;

                        // 调整为gridSize的倍数
                        int gridWidth = Math.round(newWidth / gridSize);
                        int gridHeight = Math.round(newHeight / gridSize);

                        // 确保最小尺寸
                        gridWidth = Math.max(gridWidth, MIN_SIZE);
                        gridHeight = Math.max(gridHeight, MIN_SIZE);

                        // 转换为实际像素尺寸
                        int actualWidth = (int) (gridWidth * gridSize);
                        int actualHeight = (int) (gridHeight * gridSize);

                        // 更新布局参数
                        ViewGroup.LayoutParams params = binding.linearLayout.getLayoutParams();
                        params.width = actualWidth;
                        params.height = actualHeight;
                        binding.linearLayout.setLayoutParams(params);

                        // 保存相对网格大小到ShowTextAction
                        if (action instanceof ShowTextAction showTextAction) {
                            showTextAction.setSize(new Point(gridWidth, gridHeight));
                        }

                        // 请求重新布局
                        requestLayout();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    isResizing = false;
                    requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        });
    }

    @Override
    public void refreshCardInfo() {

    }

    @Override
    public void refreshCardLockState() {
        initLock(binding.lockButton);
    }

    @Override
    public boolean check() {
        return true;
    }

    @Override
    public void addPinView(Pin pin, int offset) {
    }

    @Override
    public boolean isEmptyPosition(float x, float y) {
        float scale = getScaleX();

        List<View> views = List.of(binding.removeButton, binding.copyButton, binding.lockButton, binding.textBox, binding.dragImage);
        for (View view : views) {
            PointF pointF = DisplayUtil.getLocationRelativeToView(view, this);
            float px = pointF.x * scale;
            float py = pointF.y * scale;
            float width = view.getWidth() * scale;
            float height = view.getHeight() * scale;
            if (new RectF(px, py, px + width, py + height).contains(x, y)) return false;
        }

        return super.isEmptyPosition(x, y);
    }

    @Override
    public void bringToFront() {
        // 将ShowTextActionCard置于最底层
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            parent.removeView(this);
            parent.addView(this, 0); // 添加到索引0位置，确保在最底层
        }
    }
}