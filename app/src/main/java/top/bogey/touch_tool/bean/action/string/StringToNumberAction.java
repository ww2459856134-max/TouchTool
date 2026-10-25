package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

public class StringToNumberAction extends CalculateAction {
    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin numberPin = new Pin(new PinDouble(), R.string.pin_number_double, true);
    private final transient Pin numbersPin = new Pin(new PinList(new PinDouble()), true);

    public StringToNumberAction() {
        super(ActionType.STRING_TO_NUMBER);
        addPins(textPin, numberPin, numbersPin);
    }

    public StringToNumberAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(textPin, numberPin, numbersPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinObject text = getPinValue(runnable, textPin);
        if (text.toString().isEmpty()) return;

        PinList pinList = numbersPin.getValue(PinList.class);

        Pattern pattern = AppUtil.getPattern("-?\\d+(\\.\\d+)?");
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(text.toString());

        while (matcher.find()) {
            pinList.add(new PinDouble(Double.parseDouble(matcher.group())));
        }
        if (!pinList.isEmpty()) {
            numberPin.setValue(pinList.get(0));
        }
    }
}
