package top.bogey.touch_tool.bean.action.parent;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public abstract class CalculateAction extends Action {
    protected final transient Pin preDelayPin = new NotLinkAblePin(new PinInteger(0), R.string.node_pre_delay);
    protected final transient Pin preDelayMaxPin = new NotLinkAblePin(new PinInteger(0), R.string.node_pre_delay_max);
    private final transient boolean hasDelayPins;

    public CalculateAction(ActionType type) {
        super(type);
        hasDelayPins = Action.isNodeCategory(type);
        if (hasDelayPins) addPins(preDelayPin, preDelayMaxPin);
    }

    public CalculateAction(JsonObject jsonObject) {
        super(jsonObject);
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

    @Override
    public final void execute(TaskRunnable runnable, Pin pin) {

    }

    @Override
    public void resetReturnValue(TaskRunnable runnable, Pin pin) {
        for (Pin p : getPins()) {
            if (p.isOut()) {
                if (p.getValue() instanceof PinObject pinObject) {
                    pinObject.reset();
                }
            }
        }
    }
}
