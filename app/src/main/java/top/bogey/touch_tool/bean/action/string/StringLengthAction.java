package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 文本长度
public class StringLengthAction extends CalculateAction {
    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin lengthPin = new Pin(new PinInteger(0), R.string.string_length_action_length, true);

    public StringLengthAction() {
        super(ActionType.STRING_LENGTH);
        addPins(textPin, lengthPin);
    }

    public StringLengthAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(textPin, lengthPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        String text = getPinValue(runnable, textPin).toString();
        // 按码点统计，emoji 等增补字符算一个字
        lengthPin.getValue(PinInteger.class).setValue(text.codePointCount(0, text.length()));
    }
}
