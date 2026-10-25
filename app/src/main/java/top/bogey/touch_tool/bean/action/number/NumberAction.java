package top.bogey.touch_tool.bean.action.number;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;

public abstract class NumberAction extends CalculateAction {
    protected final transient Pin firstPin = new Pin(new PinDouble(), R.string.pin_number_double);
    protected final transient Pin secondPin = new Pin(new PinDouble(), R.string.pin_number_double);
    protected final transient Pin resultPin = new Pin(new PinDouble(), R.string.pin_number_double, true);

    public NumberAction(ActionType type) {
        super(type);
        addPins(firstPin, secondPin, resultPin);
    }

    public NumberAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(firstPin, secondPin, resultPin);
    }
}
