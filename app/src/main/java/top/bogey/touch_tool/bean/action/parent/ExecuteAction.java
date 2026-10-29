package top.bogey.touch_tool.bean.action.parent;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.PinValueArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.service.TaskRunnable;

public abstract class ExecuteAction extends Action {
    // 执行前延迟：默认 0 不等待；置为第2页针脚（isHide），所有可执行动作通用
    // 必须排在 inPin/outPin 之前 reAdd，旧任务反序列化时才不会误吃子类针脚
    protected final transient Pin preDelayPin = new Pin(new PinValueArea(0, 0), R.string.execute_before_delay);
    protected final transient Pin inPin = new Pin(new PinExecute(), R.string.pin_execute);
    protected final transient Pin outPin = new Pin(new PinExecute(), R.string.pin_execute, true);

    public ExecuteAction(ActionType type) {
        super(type);
        preDelayPin.setHide(true);
        addPin(preDelayPin);
        addPins(inPin, outPin);
    }

    public ExecuteAction(JsonObject jsonObject) {
        super(jsonObject);
        preDelayPin.setHide(true);
        reAddPins(preDelayPin, inPin, outPin);
    }

    public Pin getPreDelayPin() {
        return preDelayPin;
    }

    // 读取执行前延迟（毫秒），支持随机区间
    public long getPreDelay(TaskRunnable runnable) {
        PinValueArea delay = getPinValue(runnable, preDelayPin);
        return delay == null ? 0 : delay.getRandomValue();
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
