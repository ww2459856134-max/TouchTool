package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

public class CheckCaptureReadyAction extends CalculateAction {
    private final transient Pin valuePin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);

    public CheckCaptureReadyAction() {
        super(ActionType.CHECK_CAPTURE_READY);
        addPin(valuePin);
    }

    public CheckCaptureReadyAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(valuePin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        MainAccessibilityService service = MainApplication.getInstance().getService();
        valuePin.getValue(PinBoolean.class).setValue(service != null && service.isCaptureEnabled());
    }
}
