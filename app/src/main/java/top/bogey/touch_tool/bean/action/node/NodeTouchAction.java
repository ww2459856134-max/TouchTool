package top.bogey.touch_tool.bean.action.node;

import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonObject;

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
    // 执行通道：无障碍 / HID 触摸（ESP32，取设置页全局配置），失败自动回退无障碍
    private final transient Pin channelPin = new NotLinkAblePin(new PinSingleSelect(R.array.touch_image_channel), R.string.touch_image_channel);

    public NodeTouchAction() {
        super(ActionType.NODE_TOUCH);
        addPins(nodePin, ltPin, elsePin, precisePin, posTypePin, holdTimePin, repeatCountPin, repeatIntervalPin, channelPin);
    }

    public NodeTouchAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, ltPin, elsePin, precisePin, posTypePin, holdTimePin, repeatCountPin, repeatIntervalPin, channelPin);
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
                // HID 通道：把控件区域换算成坐标，通过 ESP32 蓝牙 HID 点击；失败回退无障碍
                PinSingleSelect channel = getPinValue(runnable, channelPin);
                if (channel != null && channel.getIndex() == 1
                        && runHidTouch(runnable, nodeInfo, precise.getValue(), posIndex, holdMs, longTouch.getValue(), count, intervalMs)) {
                    executeNext(runnable, outPin);
                    return;
                }
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
                        } else {
                            if (targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                executeNext(runnable, outPin);
                                return;
                            }
                        }
                    }
                }
            }
        }
        markUnachieved();
        executeNext(runnable, elsePin);
    }

    public Pin getNodePin() {
        return nodePin;
    }

    // 通过 ESP32 HID 点击控件：按九宫格取坐标，支持长按/连点；成功返回 true
    private boolean runHidTouch(TaskRunnable runnable, NodeInfo nodeInfo, boolean precise,
                                int posIndex, long holdMs, boolean longTouch, int count, long intervalMs) {
        String host = top.bogey.touch_tool.utils.HidClient.getConfigHost();
        int port = top.bogey.touch_tool.utils.HidClient.getConfigPort();
        if (host == null) {
            android.widget.Toast.makeText(top.bogey.touch_tool.MainApplication.getInstance(),
                    "[HID] 请先在 设置 中配置 ESP32 设备IP，回退无障碍", android.widget.Toast.LENGTH_SHORT).show();
            return false;
        }
        // 手势作用于实际接收点击的区域：非精确时允许上溯一次找可用父控件（与无障碍手势路径一致）
        NodeInfo target = nodeInfo;
        if (!precise) {
            NodeInfo usableParent = nodeInfo.findUsableParent();
            if (usableParent != null) target = usableParent;
        }
        if (target == null || target.area == null || target.area.width() <= 0 || target.area.height() <= 0) {
            runnable.addLog(new top.bogey.touch_tool.bean.other.log.LogInfo(
                    new top.bogey.touch_tool.bean.other.log.NormalLog("[HID] 控件区域无效，回退无障碍")), 0);
            return false;
        }

        // 九宫格定位（与 NodeActionHelper 一致）
        float[] posX = {0.5f, 0.25f, 0.5f, 0.75f, 0.25f, 0.75f, 0.25f, 0.5f, 0.75f};
        float[] posY = {0.5f, 0.25f, 0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f, 0.75f};
        int index = posIndex >= 0 && posIndex < 9 ? posIndex : 0;
        android.graphics.Rect area = target.area;
        int x = (int) (area.left + area.width() * posX[index]);
        int y = (int) (area.top + area.height() * posY[index]);

        long duration = holdMs > 0 ? holdMs : (longTouch ? 600 : 60);
        int times = Math.max(1, count);

        android.graphics.Point p = new android.graphics.Point(x, y);
        for (int i = 0; i < times; i++) {
            if (i > 0 && intervalMs > 0) runnable.sleep(intervalMs);
            StringBuilder hexLog = new StringBuilder();
            boolean ok = top.bogey.touch_tool.utils.HidClient.sendGesture(host, port,
                    java.util.Arrays.asList(p, p), duration, hexLog);
            if (!ok) {
                runnable.addLog(new top.bogey.touch_tool.bean.other.log.LogInfo(
                        new top.bogey.touch_tool.bean.other.log.NormalLog("[HID] 发送失败，回退无障碍: " + hexLog)), 0);
                return false;
            }
            // 等固件播完本次手势
            runnable.sleep((int) Math.min(duration + 30, 60000));
        }
        top.bogey.touch_tool.ui.custom.float_view.TouchPathFloatView.showGesture(x, y);
        return true;
    }
}
