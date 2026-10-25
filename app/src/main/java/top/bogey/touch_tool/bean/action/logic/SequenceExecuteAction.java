package top.bogey.touch_tool.bean.action.logic;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.DynamicPinsAction;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinAdd;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.special_pin.AlwaysShowPin;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public class SequenceExecuteAction extends Action implements DynamicPinsAction {
    private final static Pin morePin = new Pin(new PinExecute(), R.string.pin_execute, true);

    private final transient Pin inPin = new Pin(new PinExecute(), R.string.pin_execute);
    private final transient Pin addPin = new AlwaysShowPin(new PinAdd(morePin), R.string.pin_add_execute, true);
    private final transient Pin completePin = new Pin(new PinExecute(), R.string.sequence_action_complete, true);
    // 每两个分支之间的等待毫秒数，0 表示不等待；新针脚追加在末尾，旧任务按位置同步不受影响
    private final transient Pin intervalPin = new NotLinkAblePin(new PinInteger(0), R.string.sequence_action_interval);

    public SequenceExecuteAction() {
        super(ActionType.SEQUENCE_LOGIC);
        // 默认的首个分支针脚，和动态添加的分支一样可以移除
        Pin firstPin = new Pin(new PinExecute(), R.string.pin_execute, true, true);
        addPins(inPin, firstPin, addPin, completePin, intervalPin);
    }

    public SequenceExecuteAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(inPin);
        reAddPins(morePin);
        reAddPins(addPin, completePin, intervalPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {

    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        Action startAction = runnable.getAction();
        PinNumber<?> interval = getPinValue(runnable, intervalPin);
        int intervalValue = Math.max(0, interval.intValue());
        List<Pin> dynamicPins = getDynamicPins();
        for (int i = 0; i < dynamicPins.size(); i++) {
            if (runnable.isCurrentInterrupt()) return;
            if (!startAction.equals(runnable.getAction())) return;
            // 从第二个分支开始，每个分支前等待指定毫秒（可被任务停止打断）
            if (i > 0 && intervalValue > 0) runnable.sleep(intervalValue);
            if (runnable.isCurrentInterrupt()) return;
            if (!startAction.equals(runnable.getAction())) return;
            executeNext(runnable, dynamicPins.get(i));
        }
        executeNext(runnable, completePin);
    }

    @Override
    public void beforeExecuteNext(TaskRunnable runnable, Pin pin) {
        if (pin == completePin) {
            super.beforeExecuteNext(runnable, pin);
        }
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
