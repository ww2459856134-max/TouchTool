package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

public class StringReplaceAction extends CalculateAction {
    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin findPin = new Pin(new PinString(), R.string.string_replace_action_find);
    private final transient Pin replacePin = new Pin(new PinString(), R.string.string_replace_action_replace);
    private final transient Pin regexPin = new Pin(new PinBoolean(true), R.string.string_replace_action_regex);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.string_replace_action_result, true);

    public StringReplaceAction() {
        super(ActionType.STRING_REPLACE);
        addPins(textPin, findPin, replacePin, regexPin, resultPin);
    }

    public StringReplaceAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(textPin, findPin, replacePin, regexPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinObject text = getPinValue(runnable, textPin);
        PinObject find = getPinValue(runnable, findPin);
        PinObject replace = getPinValue(runnable, replacePin);
        PinBoolean regex = getPinValue(runnable, regexPin);

        if (text.toString().isEmpty()) return;
        if (find.toString().isEmpty()) {
            resultPin.getValue(PinString.class).setValue(text.toString());
        } else {
            String findString = find.toString();
            if (!regex.getValue()) {
                findString = AppUtil.formatRegex(findString);
            }
            String string = text.toString().replaceAll(findString, replace.toString());
            resultPin.getValue(PinString.class).setValue(string);
        }
    }
}
