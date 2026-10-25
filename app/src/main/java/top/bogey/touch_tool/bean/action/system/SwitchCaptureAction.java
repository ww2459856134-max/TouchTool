package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.util.concurrent.atomic.AtomicBoolean;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

public class SwitchCaptureAction extends ExecuteAction {
    private final transient Pin valuePin = new Pin(new PinBoolean(true), R.string.capture_switch_action_switch);
    private final transient Pin waitPin = new Pin(new PinBoolean(true), R.string.capture_switch_action_wait, false, false, true);

    public SwitchCaptureAction() {
        super(ActionType.SWITCH_CAPTURE);
        addPins(valuePin, waitPin);
    }

    public SwitchCaptureAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(valuePin, waitPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinBoolean value = getPinValue(runnable, valuePin);
        PinBoolean wait = getPinValue(runnable, waitPin);
        MainAccessibilityService service = MainApplication.getInstance().getService();
        AtomicBoolean needPaused = new AtomicBoolean(true);
        if (value.getValue()) {
            if (wait.getValue()) {
                boolean pause = service.startCapture(result -> {
                    needPaused.set(false);
                    runnable.resume();
                });
                if (pause) runnable.await();
            } else {
                service.startCapture(null);
            }
        } else {
            service.stopCapture();
        }
        executeNext(runnable, outPin);
    }
}
