package top.bogey.touch_tool.bean.action;

import android.graphics.Point;
import android.util.Log;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.base.Identity;
import top.bogey.touch_tool.bean.other.log.LogInfo;
import top.bogey.touch_tool.bean.other.log.NormalLog;
import top.bogey.touch_tool.bean.other.run.RunResultSaver;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.PinDescribe;
import top.bogey.touch_tool.bean.pin.PinRunValueRecorder;
import top.bogey.touch_tool.bean.pin.PinListener;
import top.bogey.touch_tool.bean.pin.pin_objects.PinAdd;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinMap;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.save.setting.SettingSaver;
import top.bogey.touch_tool.bean.save.variable.VariableSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.bean.task.Variable;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinCodeString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;

import top.bogey.touch_tool.utils.GsonUtil;
import top.bogey.touch_tool.utils.VariableInterpolation;

public abstract class Action extends Identity implements PinListener {
    // 任务中只能存在一个
    public final static int SINGLE_IN_TASK = 1;
    // 任务中这个动作互相同步数据
    public final static int SYNC_IN_TASK = 2;


    private final ActionType type;
    private final List<Pin> pins = new ArrayList<>();

    private ExpandType expandType;
    private boolean locked = false;

    // 最近一次变量插值的诊断信息（null=全部替换成功），供输出日志等动作展示
    public transient String interpolationDiag;
    private Point pos = new Point();

    protected transient List<Pin> tmpPins = new ArrayList<>();
    protected final transient Set<ActionListener> listeners = new HashSet<>();

    private transient int flag = 0;

    protected Action(ActionType type) {
        this.type = type;
        expandType = ExpandType.values()[SettingSaver.BLUEPRINT_CARD_EXPAND_STATE.get()];
    }

    protected Action(JsonObject jsonObject) {
        super(jsonObject);
        type = GsonUtil.getAsObject(jsonObject, "type", ActionType.class, null);
        assert type != null;
        expandType = GsonUtil.getAsObject(jsonObject, "expandType", ExpandType.class, ExpandType.HALF);
        locked = GsonUtil.getAsBoolean(jsonObject, "locked", false);
        pos = GsonUtil.getAsObject(jsonObject, "pos", Point.class, new Point());
        tmpPins = GsonUtil.getAsObject(jsonObject, "pins", TypeToken.getParameterized(ArrayList.class, Pin.class).getType(), new ArrayList<>());
    }

    public void addPin(Pin pin) {
        addPin(pins.size(), pin);
    }

    public void addPin(Pin flag, Pin pin) {
        int index = pins.indexOf(flag);
        if (index == -1) return;
        addPin(index, pin);
    }

    public void addPin(int index, Pin pin) {
        if (pin == null) return;
        if (getPinById(pin.getId()) != null) return;
        pins.add(index, pin);
        pin.setOwnerId(getId());
        pin.addListener(this);
        listeners.stream().filter(Objects::nonNull).forEach(listener -> listener.onPinAdded(pin, index));
    }

    public void addPins(Pin... pins) {
        for (Pin pin : pins) addPin(pin);
    }

    // 从临时列表中获取一个类似的针脚加入到正式列表中
    public void reAddPin(Pin def) {
        reAddPin(def, false);
    }

    public void reAddPin(Pin def, boolean backSync) {
        if (!tmpPins.isEmpty()) {
            Pin tmpPin = tmpPins.get(0);
            if (def.isSameClass(tmpPin)) {
                tmpPins.remove(0);
                def.sync(tmpPin);
            } else if (def.getValue() instanceof PinString && tmpPin.getValue() instanceof PinString) {
                // 字符串族子类型升级（如普通文本升级为文件路径/代码文本）：保留旧值与连线
                tmpPins.remove(0);
                def.sync(tmpPin);
            } else {
                if (backSync) {
                    tmpPins.remove(0);
                    def.setValue(tmpPin.getValue());
                    def.sync(tmpPin);
                }
            }
        }
        addPin(def);
    }

    public void reAddPins(Pin... pins) {
        List<Pin> list = new ArrayList<>(Arrays.asList(pins));
        for (Pin e : list) reAddPin(e);
    }

    // 从临时列表获取一系列类似针脚加入到正式列表中，直到出现添加针脚为止
    public void reAddPins(Pin def) {
        if (tmpPins.isEmpty()) {
            return;
        }

        Pin tmpPin = tmpPins.get(0);
        while (!tmpPin.isSameClass(PinAdd.class)) {
            if (def.isSameClass(tmpPin)) {
                Pin copy = def.newCopy();
                if (copy.getTitleId() == 0) {
                    copy.setTitle(tmpPin.getTitle());
                }
                reAddPin(copy);
            } else {
                tmpPins.remove(0);
            }
            if (tmpPins.isEmpty()) break;
            tmpPin = tmpPins.get(0);
        }
    }

    // 动态针脚工厂：动态参数卡片（DynamicParamsActionCard）的添加按钮调用
    // 需要自定义动态针脚类型的动作覆写此方法
    public Pin createDynamicPin() {
        return new Pin(new PinString(), 0, true, true);
    }

    // 从临时列表获取一系列针脚加入到正式列表中，直到出现添加针脚或到结束
    public void reAddPins(Pin def, boolean toEnd) {
        if (tmpPins.isEmpty()) {
            return;
        }

        Pin tmpPin = tmpPins.get(0);
        while (toEnd || !tmpPin.isSameClass(PinAdd.class)) {
            if (def != null) tmpPin.setTitleId(def.getTitleId());
            addPin(tmpPin);
            tmpPins.remove(0);

            if (tmpPins.isEmpty()) break;
            tmpPin = tmpPins.get(0);
        }
    }

    public void removePin(Pin pin) {
        if (pin == null) return;
        if (pins.remove(pin)) {
            pin.removeListener(this);
            listeners.stream().filter(Objects::nonNull).forEach(listener -> listener.onPinRemoved(pin));
        }
    }

    public void removePin(Task context, Pin pin) {
        if (pin == null) return;
        pin.clearLinks(context);
        removePin(pin);
    }

    public Pin getPinById(String id) {
        return pins.stream().filter(pin -> pin.getId().equals(id)).findFirst().orElse(null);
    }

    public Pin getPinByUid(String uid) {
        return pins.stream().filter(pin -> pin.getUid().equals(uid)).findFirst().orElse(null);
    }

    public Pin getPinByTitle(String title) {
        return pins.stream().filter(p -> p.isOut() && p.getTitle().equals(title)).findFirst().orElse(null);
    }

    public Pin findConnectToAblePin(Pin pin) {
        return pins.stream().filter(p -> p.linkAble() && p.linkAble(pin)).findFirst().orElse(null);
    }

    public void addListener(ActionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ActionListener listener) {
        listeners.remove(listener);
    }

    @Override
    public Action copy() {
        return GsonUtil.copy(this, Action.class);
    }

    @Override
    public Action newCopy() {
        Action copy = copy();
        copy.setId(UUID.randomUUID().toString());
        copy.setLocked(false);
        copy.getPins().forEach(pin -> {
            pin.setId(UUID.randomUUID().toString());
            pin.setOwnerId(copy.getId());
            pin.getLinks().clear();
        });

        copy.pos.offset(1, 1);
        return copy;
    }

    @Override
    public String getTitle() {
        ActionInfo info = ActionInfo.getActionInfo(getType());
        if (info == null) return "";
        return info.getTitle();
    }

    public abstract void execute(TaskRunnable runnable, Pin pin);

    // 未达成标记：动作执行成功但逻辑目标未达成（如找图未找到走了else分支）
    private transient boolean unachieved = false;

    public void markUnachieved() {
        unachieved = true;
    }

    public boolean isUnachieved() {
        return unachieved;
    }

    public void resetUnachieved() {
        unachieved = false;
    }

    public void executeNext(TaskRunnable runnable, Pin pin) {
        beforeExecuteNext(runnable, pin);

        if (runnable.isCurrentInterrupt()) return;
        if (pin == null) return;
        if (!pin.isOut()) return;

        Pin linkedPin = pin.getLinkedPin(runnable.getTask());
        if (linkedPin == null) return;
        Action action = runnable.getTask().getAction(linkedPin.getOwnerId());
        if (action == null) return;

        runnable.addExecuteProgress(action);
        runnable.addDebugLog(action, 1);
        action.resetReturnValue(runnable, linkedPin);
        action.resetUnachieved();
        // 执行前延迟：所有可执行动作通用，等待不计入动作耗时统计
        if (action instanceof ExecuteAction executeAction) {
            long preDelay = executeAction.getPreDelay(runnable);
            if (preDelay > 0) runnable.sleep(preDelay);
            if (runnable.isCurrentInterrupt()) return;
        }
        long runStartTime = System.currentTimeMillis();
        String taskId = runnable.getStartTask().getId();
        try {
            action.execute(runnable, linkedPin);
            // 执行完后记录所有可展示针脚的当前值，供长按针脚查看“上次运行值”
            for (Pin runPin : action.getPins()) {
                String desc = PinDescribe.describe(runPin.getValue());
                if (desc == null) continue;
                runPin.recordRunValue(runPin.getValue());
                PinRunValueRecorder.record(action.getId(), runPin, desc);
            }
            int status = action.isUnachieved() ? 1 : 0;
            String shot = status != 0 ? RunResultSaver.getInstance().saveFailureScreenshot(taskId) : null;
            RunResultSaver.getInstance().record(action, status, System.currentTimeMillis() - runStartTime, taskId, shot);
        } catch (Exception e) {
            String shot = RunResultSaver.getInstance().saveFailureScreenshot(taskId);
            RunResultSaver.getInstance().record(action, 2, System.currentTimeMillis() - runStartTime, taskId, shot);
            throw e;
        }
    }

    public void beforeExecuteNext(TaskRunnable runnable, Pin pin) {
        runnable.addDebugLog(this, -1);
    }

    public abstract void calculate(TaskRunnable runnable, Pin pin);

    public void resetReturnValue(TaskRunnable runnable, Pin pin) {

    }

    public <T extends PinObject> T getPinValue(TaskRunnable runnable, Pin pin) {
        if (pin.isOut()) {
            resetReturnValue(runnable, pin);
            calculate(runnable, pin);
            runnable.addDebugLog(this, 0);
            runnable.addCalculateProgress(this);
            pin.recordRunValue(pin.getValue());
            return returnValue(pin.getValue());
        }

        if (pin.isLinked()) {
            Pin linkedPin = pin.getLinkedPin(runnable.getTask());
            if (linkedPin != null) {
                Action action = runnable.getTask().getAction(linkedPin.getOwnerId());
                if (action != null) {
                    T pinValue = action.getPinValue(runnable, linkedPin);
                    pin.setValue(pinValue);
                    pin.recordRunValue(pinValue);
                    return pinValue;
                }
            }
        }
        // 变量插值：针脚自身值（未连线）中的 {变量名} 在运行时替换为变量值；返回拷贝，不污染针脚原配置
        // 代码文本（JS/SQL）除外，避免误伤代码中的大括号；任何异常都不影响任务继续执行
        // 类型判断用针脚本体值（声明类型可靠），插值结果写进拷贝；拷贝类型丢失时直接换新串
        T value = returnValue(pin.getValue());
        interpolationDiag = null;
        PinObject live = pin.getValue();
        if (live instanceof PinString src && !(live instanceof PinCodeString)
                && src.getValue() != null && src.getValue().contains("{")) {
            try {
                String original = src.getValue();
                String replaced = VariableInterpolation.interpolate(runnable.getTask(), original);
                if (value instanceof PinString copyString) {
                    copyString.setValue(replaced);
                } else {
                    @SuppressWarnings("unchecked")
                    T newValue = (T) new PinString(replaced);
                    value = newValue;
                }
                if (replaced.equals(original)) {
                    // 诊断：列出任务内与全局变量的实际值，方便定位变量找不到的原因
                    StringBuilder diag = new StringBuilder("任务变量:");
                    java.util.List<Variable> vars = runnable.getTask().getVariables();
                    if (vars.isEmpty()) diag.append(" 无");
                    for (Variable v : vars) {
                        PinObject sv = v.getSaveValue();
                        diag.append(" ").append(v.getTitle()).append("=")
                                .append(sv == null ? "空" : sv.getClass().getSimpleName() + ":" + sv);
                    }
                    diag.append("；全局变量:");
                    java.util.List<Variable> globals = VariableSaver.getInstance().getVars();
                    if (globals.isEmpty()) diag.append(" 无");
                    for (Variable v : globals) {
                        PinObject sv = v.getSaveValue();
                        diag.append(" ").append(v.getTitle()).append("=")
                                .append(sv == null ? "空" : sv.getClass().getSimpleName() + ":" + sv);
                    }
                    interpolationDiag = diag.toString();
                    runnable.addLog(new LogInfo(new NormalLog("[插值] 未替换 " + original + "；" + diag)), 0);
                }
            } catch (Throwable t) {
                runnable.addLog(new LogInfo(new NormalLog("[插值] 出错: " + t)), 0);
            }
        }
        return value;
    }

    // 控件、列表和集合类型返回引用，其他类型返回拷贝
    protected <T extends PinObject> T returnValue(T value) {
        if (value instanceof PinList || value instanceof PinMap) return value;
        return (T) value.copy();
    }

    public void check(ActionCheckResult result, Task task) {
        // 检查所有输入针脚是否真的能连上
        getPins().forEach(pin -> {
            if (!pin.isOut() && !pin.isVertical()) {
                Pin linkedPin = pin.getLinkedPin(task);
                if (linkedPin != null && !pin.linkAble(linkedPin)) {
                    result.addResult(ActionCheckResult.ResultType.ERROR, R.string.check_pin_linkable_error);
                }
            }
        });
    }

    @Override
    public void onLinkedTo(Task task, Pin origin, Pin to) {
        listeners.stream().filter(Objects::nonNull).forEach(listener -> listener.onPinChanged(origin));
    }

    @Override
    public void onUnLinkedFrom(Task task, Pin origin, Pin from) {
        listeners.stream().filter(Objects::nonNull).forEach(listener -> listener.onPinChanged(origin));
    }

    @Override
    public void onValueReplaced(Task task, Pin origin, PinBase value) {
        listeners.stream().filter(Objects::nonNull).forEach(listener -> listener.onPinChanged(origin));
    }

    @Override
    public void onValueUpdated(Task task, Pin origin, PinBase value) {
        listeners.stream().filter(Objects::nonNull).forEach(listener -> listener.onPinChanged(origin));
    }

    @Override
    public void onTitleChanged(Pin origin, String title) {
        listeners.stream().filter(Objects::nonNull).forEach(listener -> listener.onPinChanged(origin));
    }

    public ActionType getType() {
        return type;
    }

    public List<Pin> getPins() {
        return pins;
    }

    public ExpandType getExpandType() {
        return expandType;
    }

    public void setExpandType(ExpandType expandType) {
        this.expandType = expandType;
    }

    public boolean canExpand() {
        for (Pin pin : getPins()) {
            if (pin.isHide()) return true;
        }
        return false;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Point getPos() {
        return pos;
    }

    public void setPos(Point pos) {
        this.pos = pos;
    }

    public void setPos(int x, int y) {
        pos.set(x, y);
    }


    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public void addFlag(int flag) {
        this.flag |= flag;
    }

    public void toggleFlag(int flag) {
        this.flag ^= flag;
    }

    public boolean hasFlag(int flag) {
        return (this.flag & flag) != 0;
    }

    public enum ExpandType {
        NONE, HALF, FULL
    }

    public static class ActionDeserializer implements JsonDeserializer<Action> {
        @Override
        public Action deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            ActionType type = GsonUtil.getAsObject(jsonObject, "type", ActionType.class, null);
            if (type == null) return null;
            ActionInfo info = ActionInfo.getActionInfo(type);
            if (info == null) return null;
            try {
                Constructor<? extends Action> constructor = info.getClazz().getConstructor(JsonObject.class);
                return constructor.newInstance(jsonObject);
            } catch (Exception e) {
                Log.d("TAG", "deserialize action: " + info);
                Log.d("TAG", "deserialize action json: " + json);
                e.printStackTrace();
                return null;
            }
        }
    }
}
