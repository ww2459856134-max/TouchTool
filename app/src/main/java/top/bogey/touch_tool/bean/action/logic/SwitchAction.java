package top.bogey.touch_tool.bean.action.logic;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.DynamicPinsAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinAdd;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinStringExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.AlwaysShowPin;
import top.bogey.touch_tool.service.TaskRunnable;

public class SwitchAction extends Action implements DynamicPinsAction {
    private final static Pin morePin = new Pin(new PinStringExecute(), 0, true);
    private final transient Pin inPin = new Pin(new PinExecute(), R.string.pin_execute);
    private final transient Pin flagPin = new Pin(new PinString(), R.string.pin_string);

    private final transient Pin addPin = new AlwaysShowPin(new PinAdd(morePin), R.string.pin_add_execute, true);
    private final transient Pin defaultPin = new Pin(new PinExecute(), R.string.switch_action_default, true);

    public SwitchAction() {
        super(ActionType.SWITCH_LOGIC);
        addPins(inPin, flagPin, addPin, defaultPin);
    }

    public SwitchAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(inPin, flagPin);
        reAddPins(morePin);
        reAddPins(addPin, defaultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinObject flag = getPinValue(runnable, flagPin);
        Pin nextPin = defaultPin;
        for (Pin dynamicPin : getDynamicPins()) {
            if (Objects.equals(dynamicPin.getValue(PinStringExecute.class).getValue(), flag.toString())) {
                nextPin = dynamicPin;
                break;
            }
        }
        executeNext(runnable, nextPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {

    }

    @Override
    public List<Pin> getDynamicPins() {
        List<Pin> pins = new ArrayList<>();
        boolean start = false;
        for (Pin pin : getPins()) {
            if (pin == addPin) start = false;
            if (start) pins.add(pin);
            if (pin == flagPin) start = true;
        }
        return pins;
    }

}
