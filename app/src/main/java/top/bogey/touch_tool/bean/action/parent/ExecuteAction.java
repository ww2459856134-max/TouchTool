package top.bogey.touch_tool.bean.action.parent;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public abstract class ExecuteAction extends Action {
    protected final transient Pin inPin = new Pin(new PinExecute(), R.string.pin_execute);
    protected final transient Pin outPin = new Pin(new PinExecute(), R.string.pin_execute, true);

    protected final transient Pin preDelayPin = new NotLinkAblePin(new PinInteger(0), R.string.node_pre_delay);
    protected final transient Pin preDelayMaxPin = new NotLinkAblePin(new PinInteger(0), R.string.node_pre_delay_max);
    private final transient boolean hasDelayPins;

    public ExecuteAction(ActionType type) {
        super(type);
        addPins(inPin, outPin);
        hasDelayPins = Action.isNodeCategory(type);
        if (hasDelayPins) addPins(preDelayPin, preDelayMaxPin);
    }

    public ExecuteAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(inPin, outPin);
        hasDelayPins = Action.isNodeCategory(getType());
        if (hasDelayPins) addPins(preDelayPin, preDelayMaxPin);
    }

    @Override
    public long getPreDelay() {
        if (!hasDelayPins) return 0;
        long min = getDelayValue(preDelayPin);
        long max = getDelayValue(preDelayMaxPin);
        if (max > min && min >= 0) return min + (long) (Math.random() * (max - min));
        return Math.max(min, 0);
    }

    private long getDelayValue(Pin pin) {
        if (pin.getValue() instanceof PinInteger pinInteger) return pinInteger.getValue();
        return 0;
    }

    public Pin getInPin() {
        return inPin;
    }

    public Pin getOutPin() {
        return outPin;
    }

    @Override
    public final void calculate(TaskRunnable runnable, Pin pin) {

    }

    @Override
    public void resetReturnValue(TaskRunnable runnable, Pin pin) {
        if (!pin.isOut() && pin.isSameClass(PinExecute.class)) {
            for (Pin p : getPins()) {
                if (p.isOut()) {
                    if (p.getValue() instanceof PinObject pinObject) {
                        pinObject.reset();
                    }
                }
            }
        }
    }
}
