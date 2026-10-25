package top.bogey.touch_tool.bean.action.logic;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.DynamicPinsAction;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinAdd;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.AlwaysShowPin;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public class RandomExecuteAction extends ExecuteAction implements DynamicPinsAction {
    private final static Pin morePin = new Pin(new PinExecute(), R.string.pin_execute, true);

    private final transient Pin timesPin = new Pin(new PinInteger(1), R.string.random_action_times);
    private final transient Pin allowRepeatPin = new Pin(new PinBoolean(false), R.string.random_action_allow_repeat);

    private final transient Pin addPin = new AlwaysShowPin(new PinAdd(morePin), R.string.pin_add_execute, true);
    private final transient Pin completePin = new Pin(new PinExecute(), R.string.random_action_complete, true);
    // 分支权重：按分支顺序逗号分隔，如 7,3,1；留空或数量不符则平均随机
    // 新针脚追加在末尾，旧任务按位置同步不受影响
    private final transient Pin weightPin = new NotLinkAblePin(new PinString(), R.string.random_action_weight);

    private final Random random = new Random();

    public RandomExecuteAction() {
        super(ActionType.RANDOM_LOGIC);
        Pin secondPin = new Pin(new PinExecute(), R.string.pin_execute, true, true);
        addPins(timesPin, allowRepeatPin, secondPin, addPin, completePin, weightPin);
    }

    public RandomExecuteAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(timesPin, allowRepeatPin);
        reAddPins(morePin);
        reAddPins(addPin, completePin, weightPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        Action startAction = runnable.getAction();
        PinNumber<?> times = getPinValue(runnable, timesPin);
        PinBoolean allowRepeat = getPinValue(runnable, allowRepeatPin);
        List<Pin> dynamicPins = getDynamicPins();
        List<Integer> weights = parseWeights(runnable, dynamicPins.size());
        for (int i = 0; i < times.intValue(); i++) {
            if (runnable.isCurrentInterrupt()) return;
            if (!startAction.equals(runnable.getAction())) return;
            if (dynamicPins.isEmpty()) break;
            int index = weights == null ? random.nextInt(dynamicPins.size()) : weightedIndex(weights);
            if (allowRepeat.getValue()) {
                executeNext(runnable, dynamicPins.get(index));
            } else {
                Pin chosen = dynamicPins.remove(index);
                if (weights != null) weights.remove(index);
                executeNext(runnable, chosen);
            }
        }
        executeNext(runnable, completePin);
    }

    // 解析权重文本，返回与分支数量一致的权重列表；无法按分支对齐时返回 null（退回平均随机）
    private List<Integer> parseWeights(TaskRunnable runnable, int branchCount) {
        String text = getPinValue(runnable, weightPin).toString().trim();
        if (text.isEmpty()) return null;
        String[] parts = text.split("[,，;；\\s]+");
        List<Integer> weights = new ArrayList<>();
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value < 0) value = 0;
                weights.add(value);
            } catch (NumberFormatException ignored) {
            }
        }
        if (weights.size() != branchCount) return null;
        long total = 0;
        for (int weight : weights) total += weight;
        if (total <= 0) return null;
        return weights;
    }

    // 按权重随机：累计权重落点法，总权重为正数时才允许进来
    private int weightedIndex(List<Integer> weights) {
        long total = 0;
        for (int weight : weights) total += weight;
        int pick = random.nextInt((int) total);
        long cumulative = 0;
        for (int i = 0; i < weights.size(); i++) {
            cumulative += weights.get(i);
            if (pick < cumulative) return i;
        }
        return weights.size() - 1;
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
        pins.add(outPin);
        boolean start = false;
        for (Pin pin : getPins()) {
            if (pin == addPin) start = false;
            if (start) pins.add(pin);
            if (pin == allowRepeatPin) start = true;
        }
        return pins;
    }
}
