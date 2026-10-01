package top.bogey.touch_tool.bean.action.node;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

// 滚动直到出现：反复滚动直到界面出现包含指定文本的控件
public class ScrollUntilNodeAction extends ExecuteAction {
    private static final long SCROLL_INTERVAL = 500;

    private final transient Pin nodePin = new Pin(new PinNode(), R.string.node_scroll_until_action_container);
    private final transient Pin textPin = new Pin(new PinString(), R.string.node_appear_start_action_text);
    private final transient Pin directionPin = new NotLinkAblePin(new PinSingleSelect(R.array.node_scroll_direction), R.string.node_scroll_action_direction);
    private final transient Pin timeoutPin = new NotLinkAblePin(new PinInteger(10000), R.string.node_wait_action_timeout);
    private final transient Pin resultPin = new Pin(new PinNode(), R.string.pin_node, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public ScrollUntilNodeAction() {
        super(ActionType.SCROLL_UNTIL_NODE);
        addPins(nodePin, textPin, directionPin, timeoutPin, resultPin, successPin);
    }

    public ScrollUntilNodeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, textPin, directionPin, timeoutPin, resultPin, successPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String text = getPinValue(runnable, textPin).toString();
        PinSingleSelect direction = getPinValue(runnable, directionPin);
        long timeout = 10000;
        if (getPinValue(runnable, timeoutPin) instanceof PinInteger pinInteger) {
            timeout = Math.max(1000, pinInteger.getValue());
        }

        successPin.getValue(PinBoolean.class).setValue(false);
        resultPin.getValue(PinNode.class).setNodeInfo(null);

        if (text.isEmpty()) {
            executeNext(runnable, outPin);
            return;
        }

        long endTime = System.currentTimeMillis() + timeout;
        boolean forward = direction.getIndex() == 0;

        while (System.currentTimeMillis() < endTime && isServiceEnabled()) {
            // 先查找，滚动前检查一次
            AccessibilityNodeInfo found = findNodeByText(text);
            if (found != null) {
                resultPin.getValue(PinNode.class).setNodeInfo(new NodeInfo(found));
                successPin.getValue(PinBoolean.class).setValue(true);
                break;
            }

            scrollOnce(runnable, forward);
            runnable.sleep(SCROLL_INTERVAL);
        }
        executeNext(runnable, outPin);
    }

    // 在活动窗口中查找包含指定文本的控件
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

    // 滚动一次：优先对可滚动控件执行原生滚动，失败则用手势在屏幕上滑动
    private void scrollOnce(TaskRunnable runnable, boolean forward) {
        // 原生滚动
        NodeInfo container = getContainer();
        if (container != null && container.node != null) {
            int action = forward
                    ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            if (container.node.performAction(action)) return;
        }
        // 手势兜底：在活动窗口中部上下滑动
        gestureScroll(forward);
    }

    private NodeInfo getContainer() {
        NodeInfo window = NodeInfo.getActiveWindow();
        if (window == null) return null;
        // 在窗口中寻找第一个可滚动控件
        return findScrollable(window);
    }

    private NodeInfo findScrollable(NodeInfo parent) {
        if (parent.node != null && (parent.node.isScrollable())) return parent;
        for (int i = 0; i < parent.getChildCount(); i++) {
            NodeInfo child = parent.getChild(i);
            if (child == null) continue;
            NodeInfo result = findScrollable(child);
            if (result != null) return result;
        }
        return null;
    }

    // 手势滑动兜底
    private void gestureScroll(boolean forward) {
        MainAccessibilityService service = MainApplication.getInstance().getService();
        NodeInfo window = NodeInfo.getActiveWindow();
        if (service == null || window == null || window.area == null) return;

        android.graphics.Rect area = window.area;
        int cx = area.centerX();
        int top = area.top + (int) (area.height() * 0.7);
        int bottom = area.top + (int) (area.height() * 0.3);

        int startY = forward ? top : bottom;
        int endY = forward ? bottom : top;

        Path path = new Path();
        path.moveTo(cx, startY);
        path.lineTo(cx, endY);

        CountDownLatch latch = new CountDownLatch(1);
        service.runGesture(java.util.Collections.singleton(new GestureDescription.StrokeDescription(path, 0, 300)), result -> latch.countDown());
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isServiceEnabled() {
        MainAccessibilityService service = MainApplication.getInstance().getService();
        return service != null && service.isEnabled();
    }
}
