package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 正则提取：提取匹配内容的指定捕获组
public class StringRegexExtractAction extends CalculateAction {
    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin patternPin = new Pin(new PinString(), R.string.string_regex_extract_action_pattern);
    private final transient Pin groupPin = new Pin(new PinInteger(1), R.string.string_regex_extract_action_group);
    private final transient Pin matchPin = new Pin(new PinBoolean(), R.string.string_regex_extract_action_match, true);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.pin_string, true);

    public StringRegexExtractAction() {
        super(ActionType.STRING_REGEX_EXTRACT);
        addPins(textPin, patternPin, groupPin, matchPin, resultPin);
    }

    public StringRegexExtractAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(textPin, patternPin, groupPin, matchPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        String text = getPinValue(runnable, textPin).toString();
        String patternString = getPinValue(runnable, patternPin).toString();
        int group = 1;
        if (getPinValue(runnable, groupPin) instanceof PinInteger pinInteger) {
            group = pinInteger.getValue();
        }

        matchPin.getValue(PinBoolean.class).setValue(false);
        resultPin.setValue(new PinString(""));

        if (text.isEmpty() || patternString.isEmpty()) return;

        try {
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) return;
            // 组号 0 为整个匹配内容，正数超出组数时降级为整段匹配
            if (group < 0 || group > matcher.groupCount()) group = 0;
            String result = matcher.group(group);
            matchPin.getValue(PinBoolean.class).setValue(true);
            resultPin.setValue(new PinString(result == null ? "" : result));
        } catch (Exception ignored) {
            // 正则表达式无效时保持未匹配状态
        }
    }
}
