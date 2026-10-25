package top.bogey.touch_tool.bean.action.start;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinShortcutString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinUrlString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public class OutCallStartAction extends StartAction {
    private final transient Pin urlPin = new NotLinkAblePin(new PinUrlString(), R.string.out_call_start_action_url);
    private final transient Pin shortcutPin = new NotLinkAblePin(new PinShortcutString(), R.string.out_call_start_action_shortcut);

    public OutCallStartAction() {
        super(ActionType.OUT_CALL_START);
        addPins(urlPin, shortcutPin);
        setId(getId());
    }

    public OutCallStartAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(urlPin, shortcutPin);
        tmpPins.forEach(this::addPin);
        tmpPins.clear();
        setId(getId());
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        executeNext(runnable, executePin);
    }

    @Override
    public void setId(String id) {
        super.setId(id);
        urlPin.getValue(PinUrlString.class).setValue(id);
        shortcutPin.getValue(PinShortcutString.class).setValue(id);
    }
}
