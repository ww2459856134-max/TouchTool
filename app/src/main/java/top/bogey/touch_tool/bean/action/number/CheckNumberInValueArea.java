package top.bogey.touch_tool.bean.action.number;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinValueArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.service.TaskRunnable;

public class CheckNumberInValueArea extends CalculateAction {
    private final transient Pin valueAreaPin = new Pin(new PinValueArea(), R.string.pin_value_area);
    private final transient Pin numberPin = new Pin(new PinDouble(), R.string.pin_number_double);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);

    public CheckNumberInValueArea() {
        super(ActionType.CHECK_NUMBER_IN_VALUE_AREA);
        addPins(valueAreaPin, numberPin, resultPin);
    }

    public CheckNumberInValueArea(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(valueAreaPin, numberPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinValueArea valueArea = getPinValue(runnable, valueAreaPin);
        PinNumber<?> number = getPinValue(runnable, numberPin);
        resultPin.getValue(PinBoolean.class).setValue(valueArea.getMin() <= number.doubleValue() && number.doubleValue() <= valueArea.getMax());
    }
}
