package top.bogey.touch_tool.bean.action.parent;

import com.google.gson.JsonObject;

import java.util.ArrayList;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.PinValueArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public abstract class ExecuteAction extends Action {
    protected final transient Pin inPin = new Pin(new PinExecute(), R.string.pin_execute);
    protected final transient Pin outPin = new Pin(new PinExecute(), R.string.pin_execute, true);

    // 前置延时（ms），沿用延时动作卡片的数值区间：下限=固定延时，上限=区间上界，锁定后为固定值
    protected final transient Pin preDelayPin = new NotLinkAblePin(new PinValueArea(0, 0), R.string.node_pre_delay);
    private final transient boolean hasDelayPins;

    public ExecuteAction(ActionType type) {
        super(type);
        addPins(inPin, outPin);
        hasDelayPins = Action.isNodeCategory(type);
        if (hasDelayPins) addPins(preDelayPin);
    }

    public ExecuteAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(inPin, outPin);
        hasDelayPins = Action.isNodeCategory(getType());
        // 按值类从队列任意位置提取，与保存顺序完全解耦：
        // 控件类动作自身没有 PinValueArea 针脚（延时动作不在本队列），
        // 队列中的 PinValueArea 只可能是前置延时，按位置消费反而会与子类针脚错位（v47/v53 事故根因）
        if (hasDelayPins) recoverPreDelayPin();
    }

    private void recoverPreDelayPin() {
        String title = MainApplication.getInstance().getString(R.string.node_pre_delay);
        for (Pin tmpPin : new ArrayList<>(tmpPins)) {
            if (tmpPin.getValue() instanceof PinValueArea
                    && (title.isEmpty() || title.equals(tmpPin.getTitle()))) {
                tmpPins.remove(tmpPin);
                preDelayPin.sync(tmpPin);
                break;
            }
        }
    }

    @Override
    public long getPreDelay() {
        if (!hasDelayPins) return 0;
        if (preDelayPin.getValue() instanceof PinValueArea pinValueArea) return pinValueArea.getRandomValue();
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
