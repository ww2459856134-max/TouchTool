package top.bogey.touch_tool.bean.action.number;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.service.TaskRunnable;

public class NumberAbsAction extends CalculateAction {
    private final transient Pin doublePin = new Pin(new PinDouble(), R.string.pin_number_double);
    private final transient Pin valuePin = new Pin(new PinDouble(), R.string.number_abs_action_value, true);

    public NumberAbsAction() {
        super(ActionType.NUMBER_ABS);
        addPins(doublePin, valuePin);
    }

    public NumberAbsAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(doublePin, valuePin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinNumber<?> doubleValue = getPinValue(runnable, doublePin);
        valuePin.getValue(PinDouble.class).setValue(Math.abs(doubleValue.doubleValue()));
    }
}
