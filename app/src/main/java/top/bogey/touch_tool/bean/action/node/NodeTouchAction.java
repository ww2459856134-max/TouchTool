package top.bogey.touch_tool.bean.action.node;

import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public class NodeTouchAction extends ExecuteAction {
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node);
    private final transient Pin ltPin = new Pin(new PinBoolean(), R.string.node_touch_action_long);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.node_touch_action_else, true);
    private final transient Pin precisePin = new Pin(new PinBoolean(), R.string.node_touch_action_precise);
    // 以下新针脚追加在末尾，旧任务按位置同步不受影响
    // 点击位置（九宫格）：0 中心，1-8 九宫格其余位置
    private final transient Pin posTypePin = new Pin(new PinSingleSelect(R.array.touch_pos_type, 0), R.string.node_touch_action_pos);
    // 按住时长（毫秒）：0 = 自动（点击 60ms，勾选长按时 600ms）
    private final transient Pin holdTimePin = new Pin(new PinInteger(0), R.string.node_touch_action_hold);
    // 点击次数：大于 1 时连点
    private final transient Pin repeatCountPin = new Pin(new PinInteger(1), R.string.node_touch_action_repeat);
    // 连点间隔（毫秒）
    private final transient Pin repeatIntervalPin = new Pin(new PinInteger(100), R.string.node_touch_action_interval);
    // 运行诊断：显示失败具体原因与针脚恢复情况（排查用）
    private final transient Pin diagPin = new NotLinkAblePin(new PinString(), R.string.node_touch_diag);

    public NodeTouchAction() {
        super(ActionType.NODE_TOUCH);
        addPins(nodePin, ltPin, elsePin, precisePin, posTypePin, holdTimePin, repeatCountPin, repeatIntervalPin);
        addPin(diagPin);
    }

    public NodeTouchAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, ltPin, elsePin, precisePin, posTypePin, holdTimePin, repeatCountPin, repeatIntervalPin);
        reAddPins(diagPin);
        // 恢复诊断：针脚队列仍有滞留 = 恢复错位的铁证，直接写进诊断针脚供弹窗查看
        StringBuilder leftover = new StringBuilder();
        String diagTitle = MainApplication.getInstance().getString(R.string.node_touch_diag);
        for (Pin tmpPin : tmpPins) {
            if (diagTitle.equals(tmpPin.getTitle())) continue;
            leftover.append(tmpPin.getTitle()).append("(").append(tmpPin.getValue().getClass().getSimpleName()).append(") ");
        }
        if (leftover.length() > 0) {
            diagPin.setValue(new PinString("加载错位! 滞留针脚: " + leftover));
        }
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinNode node = getPinValue(runnable, nodePin);
        if (node != null) {
            PinBoolean longTouch = getPinValue(runnable, ltPin);
            PinBoolean precise = getPinValue(runnable, precisePin);
            PinSingleSelect posType = getPinValue(runnable, posTypePin);
            PinNumber<?> holdTime = getPinValue(runnable, holdTimePin);
            PinNumber<?> repeatCount = getPinValue(runnable, repeatCountPin);
            PinNumber<?> repeatInterval = getPinValue(runnable, repeatIntervalPin);

            int posIndex = posType.getIndex();
            long holdMs = Math.max(0, holdTime.longValue());
            int count = Math.max(1, repeatCount.intValue());
            long intervalMs = Math.max(0, repeatInterval.longValue());

            // 自定义位置 / 按住时长 / 连点时，系统点击无法表达，走手势路径
            boolean gestureMode = posIndex != 0 || holdMs > 0 || count > 1;

            NodeInfo nodeInfo = node.getNodeInfo();
            if (nodeInfo != null) {
                if (gestureMode) {
                    // 手势作用于实际接收点击的区域：非精确时允许上溯一次找可用父控件
                    NodeInfo target = nodeInfo;
                    if (!precise.getValue()) {
                        NodeInfo usableParent = nodeInfo.findUsableParent();
                        if (usableParent != null) target = usableParent;
                    }
                    long duration = holdMs > 0 ? holdMs : (longTouch.getValue() ? 600 : 0);
                    if (NodeActionHelper.gestureClick(target, posIndex, duration, count, intervalMs, runnable)) {
                        executeNext(runnable, outPin);
                        return;
                    }
                    setDiag("手势点击失败(位置=" + posIndex + ",时长=" + duration + ",次数=" + count + ")");
                } else {
                    AccessibilityNodeInfo targetNode;
                    if (precise.getValue()) {
                        targetNode = nodeInfo.node;
                    } else {
                        NodeInfo usableParent = nodeInfo.findUsableParent();
                        targetNode = usableParent != null ? usableParent.node : null;
                    }
                    if (targetNode != null) {
                        if (longTouch.getValue()) {
                            if (targetNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                                executeNext(runnable, outPin);
                                return;
                            }
                            setDiag("系统长按被拒绝(performAction=false)");
                        } else {
                            if (targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                executeNext(runnable, outPin);
                                return;
                            }
                            setDiag("系统点击被拒绝(performAction=false)");
                        }
                    } else {
                        setDiag("找不到可点击目标(控件及其父控件均不可点击)");
                    }
                }
            } else {
                setDiag("控件引用已失效(nodeInfo=null)");
            }
        } else {
            setDiag("控件输入为空(连线断开或上游无输出)");
        }
        markUnachieved();
        executeNext(runnable, elsePin);
    }

    private void setDiag(String reason) {
        diagPin.setValue(new PinString(reason));
    }

    public Pin getNodePin() {
        return nodePin;
    }
}
