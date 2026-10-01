package top.bogey.touch_tool.bean.action.app;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

public class StringToAppAction extends CalculateAction {
    private final transient Pin packagePin = new Pin(new PinString(), R.string.string_to_app_action_package);
    private final transient Pin activityPin = new Pin(new PinString(), R.string.string_to_app_action_activity);
    private final transient Pin appPin = new Pin(new PinApplication(), R.string.pin_app, true);

    public StringToAppAction() {
        super(ActionType.STRING_TO_APP);
        addPins(packagePin, activityPin, appPin);
    }

    public StringToAppAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(packagePin, activityPin, appPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinObject packageName = getPinValue(runnable, packagePin);
        PinObject activityName = getPinValue(runnable, activityPin);

        PinApplication application = new PinApplication(packageName.toString(), activityName.toString());
        appPin.setValue(application);
    }
}
