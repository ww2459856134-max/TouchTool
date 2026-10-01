package top.bogey.touch_tool.bean.action.app;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinApplications;
import top.bogey.touch_tool.service.TaskRunnable;

public class CheckInAppAction extends CalculateAction {
    private final transient Pin appPin = new Pin(new PinApplications(), R.string.pin_app);
    private final transient Pin checkAppPin = new Pin(new PinApplication(), R.string.check_in_app_action_package);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);

    public CheckInAppAction() {
        super(ActionType.CHECK_IN_APPLICATION);
        addPins(resultPin, appPin, checkAppPin);
    }

    public CheckInAppAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(resultPin, appPin, checkAppPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinApplications apps = PinApplications.convertAppList(getPinValue(runnable, appPin));
        PinApplication checkApp = getPinValue(runnable, checkAppPin);
        resultPin.getValue(PinBoolean.class).setValue(apps.contains(checkApp));
    }
}
