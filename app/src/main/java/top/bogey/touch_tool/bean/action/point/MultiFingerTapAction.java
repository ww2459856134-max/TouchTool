package top.bogey.touch_tool.bean.action.point;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

// 多指点击：多根手指在中心坐标周围水平分布同时点击
public class MultiFingerTapAction extends ExecuteAction {
    private final transient Pin xPin = new Pin(new PinInteger(500), R.string.multi_finger_action_x);
    private final transient Pin yPin = new Pin(new PinInteger(500), R.string.multi_finger_action_y);
    private final transient Pin fingerPin = new NotLinkAblePin(new PinInteger(2), R.string.multi_finger_action_count);
    private final transient Pin spacingPin = new NotLinkAblePin(new PinInteger(200), R.string.multi_finger_action_spacing);
    private final transient Pin timePin = new Pin(new PinInteger(80), R.string.multi_finger_action_time, false, false, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public MultiFingerTapAction() {
        super(ActionType.MULTI_FINGER_TAP);
        addPins(xPin, yPin, fingerPin, spacingPin, timePin, successPin);
    }

    public MultiFingerTapAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(xPin, yPin, fingerPin, spacingPin, timePin, successPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        int x = getInt(runnable, xPin, 500);
        int y = getInt(runnable, yPin, 500);
        int fingers = Math.max(2, Math.min(5, getInt(runnable, fingerPin, 2)));
        int spacing = Math.max(50, getInt(runnable, spacingPin, 200));
        int time = Math.max(30, getInt(runnable, timePin, 80));

        successPin.getValue(PinBoolean.class).setValue(false);

        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null) {
            executeNext(runnable, outPin);
            return;
        }

        // 手指水平等距分布在中心两侧
        java.util.Set<GestureDescription.StrokeDescription> strokes = new java.util.HashSet<>();
        for (int i = 0; i < fingers; i++) {
            int fingerX = (int) (x + (i - (fingers - 1) / 2.0) * spacing);
            Path path = new Path();
            path.moveTo(fingerX, y);
            strokes.add(new GestureDescription.StrokeDescription(path, 0, time));
        }

        CountDownLatch latch = new CountDownLatch(1);
        service.runGesture(strokes, result -> latch.countDown());
        try {
            latch.await(3, TimeUnit.SECONDS);
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
}
