package top.bogey.touch_tool.bean.action.parent;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.service.TaskRunnable;

public abstract class ExecuteAction extends Action {
    protected final transient Pin inPin = new Pin(new PinExecute(), R.string.pin_execute);
    protected final transient Pin outPin = new Pin(new PinExecute(), R.string.pin_execute, true);

    public ExecuteAction(ActionType type) {
        super(type);
        addPins(inPin, outPin);
    }

    public ExecuteAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(inPin, outPin);
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
