package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import java.util.stream.IntStream;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

public class StringSplitAction extends CalculateAction {
    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin separatorPin = new Pin(new PinString(), R.string.string_split_action_split);
    private final transient Pin emptyPin = new Pin(new PinBoolean(true), R.string.string_split_action_empty);
    private final transient Pin regexPin = new Pin(new PinBoolean(true), R.string.string_split_action_regex);
    private final transient Pin resultPin = new Pin(new PinList(new PinString()), true);

    public StringSplitAction() {
        super(ActionType.STRING_SPLIT);
        addPins(textPin, separatorPin, emptyPin, regexPin, resultPin);
    }

    public StringSplitAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(textPin, separatorPin, emptyPin, regexPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinList value = resultPin.getValue(PinList.class);

        PinObject text = getPinValue(runnable, textPin);
        PinObject separator = getPinValue(runnable, separatorPin);
        PinBoolean empty = getPinValue(runnable, emptyPin);
        PinBoolean regex = getPinValue(runnable, regexPin);

        if (text.toString().isEmpty()) return;

        if (separator.toString().isEmpty()) {
            IntStream intStream = text.toString().codePoints();
            intStream.forEach(cp -> {
                String s = new String(Character.toChars(cp));
                if (empty.getValue()) {
                    s = s.trim();
                    if (s.isEmpty()) return;
                }
                value.add(new PinString(s));
            });
        } else {
            String separatorString = separator.toString();
            if (!regex.getValue()) {
                separatorString = AppUtil.formatRegex(separatorString);
            }

            String[] split = text.toString().split(separatorString);
            for (String s : split) {
                if (empty.getValue()) {
                    s = s.trim();
                    if (s.isEmpty()) continue;
                }
                value.add(new PinString(s));
            }
        }
    }
}
