package top.bogey.touch_tool.bean.action.bool;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.service.TaskRunnable;

public class BooleanNotAction extends CalculateAction {
    private final transient Pin boolPin = new Pin(new PinBoolean(), R.string.pin_boolean_condition);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);

    public BooleanNotAction() {
        super(ActionType.BOOLEAN_NOT);
        addPins(boolPin, resultPin);
    }

    public BooleanNotAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(boolPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinBoolean bool = getPinValue(runnable, boolPin);
        resultPin.getValue(PinBoolean.class).setValue(!bool.getValue());
    }
}
