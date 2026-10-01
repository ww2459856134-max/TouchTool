package top.bogey.touch_tool.bean.action.point;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;

import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

// 多指滑动：多根手指同时滑动，支持张开(放大)、捏合(缩小)与同向滑动
public class MultiFingerSwipeAction extends ExecuteAction {
    private final transient Pin xPin = new Pin(new PinInteger(500), R.string.multi_finger_action_x);
    private final transient Pin yPin = new Pin(new PinInteger(500), R.string.multi_finger_action_y);
    private final transient Pin modePin = new NotLinkAblePin(new PinSingleSelect(R.array.multi_finger_swipe_mode), R.string.multi_finger_action_mode);
    private final transient Pin directionPin = new DirectionShowablePin(new PinSingleSelect(R.array.multi_swipe_direction), R.string.multi_finger_action_direction);
    private final transient Pin fingerPin = new NotLinkAblePin(new PinInteger(2), R.string.multi_finger_action_count);
    private final transient Pin spacingPin = new NotLinkAblePin(new PinInteger(200), R.string.multi_finger_action_spacing);
    private final transient Pin distancePin = new NotLinkAblePin(new PinInteger(300), R.string.multi_finger_action_distance);
    private final transient Pin durationPin = new NotLinkAblePin(new PinInteger(400), R.string.multi_finger_action_duration);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public MultiFingerSwipeAction() {
        super(ActionType.MULTI_FINGER_SWIPE);
        addPins(xPin, yPin, modePin, directionPin, fingerPin, spacingPin, distancePin, durationPin, successPin);
    }

    public MultiFingerSwipeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(xPin, yPin, modePin, directionPin, fingerPin, spacingPin, distancePin, durationPin, successPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        int cx = getInt(runnable, xPin, 500);
        int cy = getInt(runnable, yPin, 500);
        int fingers = Math.max(2, Math.min(5, getInt(runnable, fingerPin, 2)));
        int spacing = Math.max(50, getInt(runnable, spacingPin, 200));
        int distance = Math.max(50, getInt(runnable, distancePin, 300));
        int duration = Math.max(100, getInt(runnable, durationPin, 400));

        successPin.getValue(PinBoolean.class).setValue(false);

        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null) {
            executeNext(runnable, outPin);
            return;
        }

        int mode = 0;
        if (getPinValue(runnable, modePin) instanceof PinSingleSelect select) mode = select.getIndex();

        // 手指水平等距分布，计算每根手指的起点与终点
        Set<GestureDescription.StrokeDescription> strokes = new HashSet<>();
        for (int i = 0; i < fingers; i++) {
            int startX = (int) (cx + (i - (fingers - 1) / 2.0) * spacing);
            int startY = cy;
            int endX = startX;
            int endY = startY;

            if (mode == 0) {
                // 张开：各手指沿水平方向远离中心
                double sign = Math.signum(startX - (double) cx);
                endX = startX + (int) (sign * distance / 2.0);
            } else if (mode == 1) {
                // 捏合：各手指沿水平方向靠近中心
                double sign = Math.signum(startX - (double) cx);
                endX = startX - (int) (sign * distance / 2.0);
            } else {
                // 同向滑动：按方向针脚整体移动
                int direction = 0;
                if (getPinValue(runnable, directionPin) instanceof PinSingleSelect select) direction = select.getIndex();
                switch (direction) {
                    case 0 -> endY = startY - distance;
                    case 1 -> endY = startY + distance;
                    case 2 -> endX = startX - distance;
                    default -> endX = startX + distance;
                }
            }

            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            strokes.add(new GestureDescription.StrokeDescription(path, 0, duration));
        }

        CountDownLatch latch = new CountDownLatch(1);
        service.runGesture(strokes, result -> latch.countDown());
        try {
            latch.await(duration + 2000L, TimeUnit.MILLISECONDS);
            successPin.getValue(PinBoolean.class).setValue(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executeNext(runnable, outPin);
    }

    private int getInt(TaskRunnable runnable, Pin conditionPin, int def) {
        if (getPinValue(runnable, conditionPin) instanceof PinInteger pinInteger) return pinInteger.getValue();
        return def;
    }

    // 方向针脚：仅同向滑动模式显示
    private static class DirectionShowablePin extends NotLinkAblePin {
        public DirectionShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof MultiFingerSwipeAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            return mode != null && mode.getIndex() == 2;
        }
    }
}
