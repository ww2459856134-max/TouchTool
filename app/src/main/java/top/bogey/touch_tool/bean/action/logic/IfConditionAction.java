package top.bogey.touch_tool.bean.action.logic;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.service.TaskRunnable;

public class IfConditionAction extends ExecuteAction {
    private final transient Pin conditionPin = new Pin(new PinBoolean(), R.string.pin_boolean_condition);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public IfConditionAction() {
        super(ActionType.IF_LOGIC);
        addPins(conditionPin, elsePin);
    }

    public IfConditionAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(conditionPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinBoolean condition = getPinValue(runnable, conditionPin);
        if (condition.getValue()) {
            executeNext(runnable, outPin);
        } else {
            executeNext(runnable, elsePin);
        }
    }
}
