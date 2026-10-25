package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinRingtoneString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

public class StopRingtoneAction extends ExecuteAction {
    private final transient Pin ringPin = new Pin(new PinRingtoneString(), R.string.stop_ringtone_action_ringtone);

    public StopRingtoneAction() {
        super(ActionType.STOP_RINGTONE);
        addPin(ringPin);
    }

    public StopRingtoneAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(ringPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinString ring = getPinValue(runnable, ringPin);
        MainAccessibilityService service = MainApplication.getInstance().getService();
        service.stopSound(ring.getValue());
        executeNext(runnable, outPin);
    }
}
