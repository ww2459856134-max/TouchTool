package top.bogey.touch_tool.bean.action.point;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.ui.custom.float_view.TouchPathFloatView;

// 连续点击：对指定坐标连续点击多次
public class ContinuousTapAction extends ExecuteAction {
    private final transient Pin xPin = new Pin(new PinInteger(500), R.string.multi_finger_action_x);
    private final transient Pin yPin = new Pin(new PinInteger(500), R.string.multi_finger_action_y);
    private final transient Pin repeatPin = new Pin(new PinInteger(3), R.string.touch_image_action_repeat);
    private final transient Pin intervalPin = new Pin(new PinInteger(100), R.string.touch_image_action_click_interval);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public ContinuousTapAction() {
        super(ActionType.CONTINUOUS_TAP);
        addPins(xPin, yPin, repeatPin, intervalPin, successPin);
    }

    public ContinuousTapAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(xPin, yPin, repeatPin, intervalPin, successPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        int x = getInt(runnable, xPin, 500);
        int y = getInt(runnable, yPin, 500);
        int repeat = Math.max(1, getInt(runnable, repeatPin, 3));
        int interval = Math.max(0, getInt(runnable, intervalPin, 100));

        MainAccessibilityService service = MainApplication.getInstance().getService();
        successPin.getValue(PinBoolean.class).setValue(false);
        if (service == null) {
            executeNext(runnable, outPin);
            return;
        }

        for (int i = 0; i < repeat; i++) {
            if (runnable.isCurrentInterrupt()) return;
            service.runGesture(x, y, 50, null);
            TouchPathFloatView.showGesture(x, y);
            if (i < repeat - 1 && interval > 0) runnable.sleep(interval);
        }

        successPin.getValue(PinBoolean.class).setValue(true);
        executeNext(runnable, outPin);
    }

    private int getInt(TaskRunnable runnable, Pin conditionPin, int def) {
        if (getPinValue(runnable, conditionPin) instanceof PinInteger pinInteger) return pinInteger.getValue();
        return def;
    }
}
