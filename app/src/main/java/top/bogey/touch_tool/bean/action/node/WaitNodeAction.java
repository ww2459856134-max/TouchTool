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
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

// 等待控件出现或消失
public class WaitNodeAction extends ExecuteAction {
    private static final long CHECK_INTERVAL = 300;

    private final transient Pin textPin = new Pin(new PinString(), R.string.node_appear_start_action_text);
    private final transient Pin typePin = new NotLinkAblePin(new PinSingleSelect(R.array.node_wait_type), R.string.node_wait_action_type);
    private final transient Pin timeoutPin = new NotLinkAblePin(new PinInteger(5000), R.string.node_wait_action_timeout);
    private final transient Pin resultPin = new Pin(new PinNode(), R.string.pin_node, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public WaitNodeAction() {
        super(ActionType.WAIT_NODE);
        addPins(textPin, typePin, timeoutPin, resultPin, successPin);
    }

    public WaitNodeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(textPin, typePin, timeoutPin, resultPin, successPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String text = getPinValue(runnable, textPin).toString();
        PinSingleSelect type = getPinValue(runnable, typePin);
        long timeout = 5000;
        if (getPinValue(runnable, timeoutPin) instanceof PinInteger pinInteger) {
            timeout = Math.max(300, pinInteger.getValue());
        }

        successPin.getValue(PinBoolean.class).setValue(false);
        resultPin.getValue(PinNode.class).setNodeInfo(null);

        if (text.isEmpty()) {
            executeNext(runnable, outPin);
            return;
        }

        // 等待出现：找到即成功；等待消失：找不到即成功
        boolean waitAppear = type.getIndex() == 0;
        long endTime = System.currentTimeMillis() + timeout;

        while (System.currentTimeMillis() < endTime && isServiceEnabled()) {
            AccessibilityNodeInfo found = findNodeByText(text);
            if (waitAppear && found != null) {
                resultPin.getValue(PinNode.class).setNodeInfo(new NodeInfo(found));
                successPin.getValue(PinBoolean.class).setValue(true);
                break;
            }
            if (!waitAppear && found == null) {
                successPin.getValue(PinBoolean.class).setValue(true);
                break;
            }
            runnable.sleep(CHECK_INTERVAL);
        }
        executeNext(runnable, outPin);
    }

    private AccessibilityNodeInfo findNodeByText(String text) {
        NodeInfo window = NodeInfo.getActiveWindow();
        if (window == null || window.node == null) return null;
        java.util.List<AccessibilityNodeInfo> nodes = window.node.findAccessibilityNodeInfosByText(text);
        if (nodes == null || nodes.isEmpty()) return null;
        for (AccessibilityNodeInfo node : nodes) {
            if (node != null && node.isVisibleToUser()) return node;
        }
        return null;
    }

    private boolean isServiceEnabled() {
        MainAccessibilityService service = MainApplication.getInstance().getService();
        return service != null && service.isEnabled();
    }
}
