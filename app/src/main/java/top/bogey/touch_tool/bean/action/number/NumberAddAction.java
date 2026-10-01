package top.bogey.touch_tool.bean.action.number;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.service.TaskRunnable;

public class NumberAddAction extends DynamicNumberAction {

    public NumberAddAction() {
        super(ActionType.NUMBER_ADD);
    }

    public NumberAddAction(JsonObject jsonObject) {
        super(jsonObject);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        double result = 0;
        for (Pin dynamicPin : getDynamicPins()) {
            PinNumber<?> value = getPinValue(runnable, dynamicPin);
            result += value.doubleValue();
        }
        resultPin.getValue(PinDouble.class).setValue(result);
    }
}
