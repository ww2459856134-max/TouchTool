package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinRingtoneString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

public class PlayRingtoneAction extends ExecuteAction {
    private final transient Pin ringPin = new Pin(new PinRingtoneString(), R.string.play_ringtone_action_ringtone);
    private final transient Pin usagePin = new Pin(new PinSingleSelect(R.array.media_usage), R.string.play_ringtone_action_usage);

    public PlayRingtoneAction() {
        super(ActionType.PLAY_RINGTONE);
        addPins(ringPin, usagePin);
    }

    public PlayRingtoneAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(ringPin, usagePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinString ring = getPinValue(runnable, ringPin);
        PinSingleSelect usage = getPinValue(runnable, usagePin);
        MainAccessibilityService service = MainApplication.getInstance().getService();
        service.playSound(ring.getValue(), usage.getIndex());
        executeNext(runnable, outPin);
    }
}
