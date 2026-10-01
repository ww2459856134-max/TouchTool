package top.bogey.touch_tool.bean.action.parent;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.service.TaskRunnable;

public abstract class CalculateAction extends Action {

    public CalculateAction(ActionType type) {
        super(type);
    }

    public CalculateAction(JsonObject jsonObject) {
        super(jsonObject);
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
