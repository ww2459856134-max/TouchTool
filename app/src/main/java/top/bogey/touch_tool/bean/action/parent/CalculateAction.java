package top.bogey.touch_tool.bean.action.parent;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.PinValueArea;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public abstract class CalculateAction extends Action {
    // 前置延时（ms），沿用延时动作卡片的数值区间：下限=固定延时，上限=区间上界，锁定后为固定值
    protected final transient Pin preDelayPin = new NotLinkAblePin(new PinValueArea(0, 0), R.string.node_pre_delay);
    private final transient boolean hasDelayPins;

    public CalculateAction(ActionType type) {
        super(type);
        hasDelayPins = Action.isNodeCategory(type);
        if (hasDelayPins) addPins(preDelayPin);
    }

    public CalculateAction(JsonObject jsonObject) {
        super(jsonObject);
        hasDelayPins = Action.isNodeCategory(getType());
        if (hasDelayPins) addPins(preDelayPin);
    }

    @Override
    public long getPreDelay() {
        if (!hasDelayPins) return 0;
        if (preDelayPin.getValue() instanceof PinValueArea pinValueArea) return pinValueArea.getRandomValue();
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
