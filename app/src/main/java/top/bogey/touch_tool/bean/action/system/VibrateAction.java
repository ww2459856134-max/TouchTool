package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

public class VibrateAction extends ExecuteAction {
    protected final transient Pin durationPin = new Pin(new PinInteger(1000), R.string.vibrate_action_time);

    public VibrateAction() {
        super(ActionType.VIBRATE);
        addPin(durationPin);
    }

    public VibrateAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(durationPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinNumber<?> duration = getPinValue(runnable, durationPin);
        MainAccessibilityService service = MainApplication.getInstance().getService();
        service.vibrate(duration.longValue());
        executeNext(runnable, outPin);
    }
}
