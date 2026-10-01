package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.action.parent.DynamicPinsAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinAdd;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

public class StringAppendAction extends CalculateAction implements DynamicPinsAction {
    private final static Pin morePin = new Pin(new PinString(), R.string.pin_string);

    private final transient Pin firstPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin secondPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.pin_string, true);
    private final transient Pin addPin = new Pin(new PinAdd(morePin), R.string.pin_add_pin);

    public StringAppendAction() {
        super(ActionType.STRING_APPEND);
        addPins(firstPin, secondPin, resultPin, addPin);
    }

    public StringAppendAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(firstPin, secondPin, resultPin);
        reAddPins(morePin);
        reAddPin(addPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        StringBuilder builder = new StringBuilder();
        getDynamicPins().forEach(dynamicPin -> {
            PinObject string = getPinValue(runnable, dynamicPin);
            if (string == null) return;
            builder.append(string);
        });
        resultPin.getValue(PinString.class).setValue(builder.toString());
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
