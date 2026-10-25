package top.bogey.touch_tool.bean.action.logic;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.DynamicPinsAction;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinAdd;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.special_pin.AlwaysShowPin;
import top.bogey.touch_tool.service.TaskRunnable;

/**
 * 否则如果…就…
 * <p>
 * 与「如果…就…」的区别：「否则」也带条件，条件从上往下短路求值，
 * 第一个成立的分支执行后结束；全部不成立才走最后的「否则」。
 * <p>
 * 每个分支 = 条件针脚 + 执行针脚，成对添加（PinAdd 内放两个针脚，成组针脚会并到同一行）。
 * 所有分支针脚都是动态针脚，解析走 reAddPins(Pin...)，
 * 这样老文件里没有的类型不会被误消费，以后增删针脚也不会让老 .tt 错位。
 */
public class ElseIfAction extends ExecuteAction implements DynamicPinsAction {
    private final static Pin morePin = new Pin(new PinBoolean(), R.string.pin_boolean_condition);
    private final static Pin moreExecutePin = new Pin(new PinExecute(), R.string.pin_execute, true);

    private final transient Pin addPin = new AlwaysShowPin(new PinAdd(Arrays.asList(morePin, moreExecutePin)), R.string.pin_add_if, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public ElseIfAction() {
        super(ActionType.ELSE_IF_LOGIC);
        // 默认的第一组，和动态添加的分支一样可以移除
        Pin firstConditionPin = new Pin(new PinBoolean(), R.string.pin_boolean_condition, false, true);
        Pin firstExecutePin = new Pin(new PinExecute(), R.string.pin_execute, true, true);
        addPins(firstConditionPin, firstExecutePin, addPin, elsePin);
    }

    public ElseIfAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(morePin);
        reAddPins(addPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        List<Pin> branches = getDynamicPins();
        for (int i = 0; i < branches.size(); i++) {
            Pin conditionPin = branches.get(i);
            if (!(conditionPin.getValue() instanceof PinBoolean)) continue;
            PinBoolean condition = getPinValue(runnable, conditionPin);
            if (!condition.getValue()) continue;

            // 条件成立：执行配对的分支，后续条件不再求值
            int executeIndex = i + 1;
            if (executeIndex < branches.size()) {
                Pin executePin = branches.get(executeIndex);
                if (executePin.getValue() instanceof PinExecute) {
                    executeNext(runnable, executePin);
                    return;
                }
            }
            // 条件后面没有配对的执行针脚，直接收尾
            executeNext(runnable, outPin);
            return;
        }

        markUnachieved();
        executeNext(runnable, elsePin);
    }

    @Override
    public List<Pin> getDynamicPins() {
        List<Pin> pins = new ArrayList<>();
        boolean start = false;
        for (Pin pin : getPins()) {
            if (pin == addPin) start = false;
            if (start) pins.add(pin);
            if (pin == inPin) start = true;
        }
        return pins;
    }
}
