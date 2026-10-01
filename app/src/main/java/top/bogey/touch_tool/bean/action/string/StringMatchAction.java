package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleLineString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

public class StringMatchAction extends ExecuteAction {

    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin matchPin = new Pin(new PinSingleLineString(), R.string.string_match_action_match);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);
    private final transient Pin resultPin = new Pin(new PinList(new PinString()), R.string.string_match_action_match_all_result, true);
    private final transient Pin matchesPin = new Pin(new PinList(new PinString()), R.string.string_match_action_match_all_matchers, true);

    public StringMatchAction() {
        super(ActionType.STRING_REGEX);
        addPins(textPin, matchPin, elsePin, resultPin, matchesPin);
    }

    public StringMatchAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(textPin, matchPin, elsePin, resultPin, matchesPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinObject text = getPinValue(runnable, textPin);
        PinObject match = getPinValue(runnable, matchPin);

        PinList matches = matchesPin.getValue();
        PinList result = resultPin.getValue();

        Pattern pattern = AppUtil.getPattern(match.toString().trim());
        if (pattern != null) {
            Matcher matcher = pattern.matcher(text.toString());
            while (matcher.find()) {
                for (int i = 0; i < matcher.groupCount(); i++) {
                    String group = matcher.group(i + 1);
                    matches.add(new PinString(group));
                }
                result.add(new PinString(matcher.group()));
            }

            if (!result.isEmpty()) {
                executeNext(runnable, outPin);
                return;
            }
        }
        executeNext(runnable, elsePin);
    }
}
