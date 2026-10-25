package top.bogey.touch_tool.bean.action.logic;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.FindExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.service.TaskRunnable;

public class WaitConditionAction extends FindExecuteAction {
    private final transient Pin conditionPin = new Pin(new PinBoolean(), R.string.pin_boolean_condition);

    public WaitConditionAction() {
        super(ActionType.WAIT_IF_LOGIC);
        timeoutPin.setHide(false);
        intervalPin.setHide(false);
        addPin(conditionPin);
    }

    public WaitConditionAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(conditionPin);
    }

    @Override
    public boolean find(TaskRunnable runnable) {
        PinBoolean condition = getPinValue(runnable, conditionPin);
        return condition.getValue();
    }
}
