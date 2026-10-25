package top.bogey.touch_tool.bean.action.number;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.service.TaskRunnable;

public class NumberToIntegerAction extends CalculateAction {
    private final transient Pin doublePin = new Pin(new PinDouble(), R.string.pin_number_double);
    private final transient Pin typePin = new Pin(new PinSingleSelect(R.array.to_int_type), R.string.number_to_integer_action_type);
    private final transient Pin integerPin = new Pin(new PinInteger(), R.string.pin_number_integer, true);

    public NumberToIntegerAction() {
        super(ActionType.NUMBER_TO_INT);
        addPins(doublePin, typePin, integerPin);
    }

    public NumberToIntegerAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(doublePin, typePin, integerPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinNumber<?> doubleValue = getPinValue(runnable, doublePin);
        PinSingleSelect type = getPinValue(runnable, typePin);
        int value = 0;
        switch (type.getIndex()) {
            case 0 -> value = (int) Math.round(doubleValue.doubleValue());
            case 1 -> value = (int) Math.ceil(doubleValue.doubleValue());
            case 2 -> value = (int) Math.floor(doubleValue.doubleValue());
        }
        integerPin.getValue(PinInteger.class).setValue(value);
    }
}
