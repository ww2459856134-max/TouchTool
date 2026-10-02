package top.bogey.touch_tool.bean.action.logic;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public class WhileLoopAction extends ExecuteAction {
    private final transient Pin breakPin = new Pin(new PinExecute(), R.string.while_loop_action_break);
    private final transient Pin conditionPin = new Pin(new PinBoolean(), R.string.pin_boolean_condition);
    private final transient Pin maxCountPin = new NotLinkAblePin(new PinInteger(0), R.string.while_loop_action_max_count, false, false, true);
    private final transient Pin completePin = new Pin(new PinExecute(), R.string.while_loop_action_complete, true);
    private transient boolean isBreak = false;

    public WhileLoopAction() {
        super(ActionType.WHILE_LOGIC);
        addPins(breakPin, conditionPin, maxCountPin, completePin);
    }

    public WhileLoopAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(breakPin, conditionPin, maxCountPin, completePin);
        // 高级参数固定归入第二页
        maxCountPin.setHide(true);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        if (pin == inPin) {
            isBreak = false;
            Action startAction = runnable.getAction();
            PinBoolean condition = getPinValue(runnable, conditionPin);
            int maxCount = 0;
            if (getPinValue(runnable, maxCountPin) instanceof PinInteger pinInteger) {
                maxCount = Math.max(0, pinInteger.getValue());
            }
            int count = 0;
            while (condition.getValue()) {
                if (runnable.isCurrentInterrupt()) return;
                if (!startAction.equals(runnable.getAction())) return;
                if (isBreak) break;
                // 最大迭代防呆：超过次数强制结束循环，防止条件恒真卡死任务
                if (maxCount > 0 && count >= maxCount) break;
                count++;
                executeNext(runnable, outPin);
                condition = getPinValue(runnable, conditionPin);
            }
            executeNext(runnable, completePin);
        } else {
            isBreak = true;
            super.beforeExecuteNext(runnable, null);
        }
    }
}
