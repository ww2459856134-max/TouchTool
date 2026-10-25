package top.bogey.touch_tool.bean.action.number;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.DynamicPinsAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinAdd;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;

public abstract class DynamicNumberAction extends NumberAction implements DynamicPinsAction {
    private final static Pin morePin = new Pin(new PinDouble(), R.string.pin_number_double);
    private final transient Pin addPin = new Pin(new PinAdd(morePin), R.string.pin_add_pin);

    public DynamicNumberAction(ActionType type) {
        super(type);
        addPin(addPin);
    }

    public DynamicNumberAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(morePin);
        reAddPin(addPin);
    }

    @Override
    public List<Pin> getDynamicPins() {
        List<Pin> pins = new ArrayList<>();
        pins.add(firstPin);
        pins.add(secondPin);
        boolean start = false;
        for (Pin pin : getPins()) {
            if (pin == addPin) start = false;
            if (start) pins.add(pin);
            if (pin == resultPin) start = true;
        }
        return pins;
    }
}
