package top.bogey.touch_tool.bean.action.number;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.service.TaskRunnable;

public class NumberEqualAction extends NumberResultAction {
    private final transient Pin offsetPin = new Pin(new PinDouble(0.0001), R.string.number_equal_action_offset, false, false, true);

    public NumberEqualAction() {
        super(ActionType.NUMBER_EQUAL);
        addPin(offsetPin);
    }

    public NumberEqualAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(offsetPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinNumber<?> first = getPinValue(runnable, firstPin);
        PinNumber<?> second = getPinValue(runnable, secondPin);
        PinNumber<?> offset = getPinValue(runnable, offsetPin);
        boolean result = Math.abs(first.doubleValue() - second.doubleValue()) < offset.doubleValue();
        resultPin.getValue(PinBoolean.class).setValue(result);
    }
}
