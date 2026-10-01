package top.bogey.touch_tool.bean.action.node;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.service.MainAccessibilityService;

// 控件操作降级助手：无障碍操作不被支持时，用长按菜单方式兜底
public class NodeActionHelper {

    // 长按控件中心点，唤出文本选择菜单
    public static boolean longPress(NodeInfo nodeInfo) {
        if (nodeInfo == null || nodeInfo.area == null) return false;
        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null) return false;

        android.graphics.Rect area = nodeInfo.area;
        Path path = new Path();
        path.moveTo(area.centerX(), area.centerY());

        CountDownLatch latch = new CountDownLatch(1);
        service.runGesture(java.util.Collections.singleton(
                new GestureDescription.StrokeDescription(path, 0, 600)), result -> latch.countDown());
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 等待文本选择菜单弹出
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true;
    }

    // 在当前界面（含弹出的文本选择菜单）中查找并点击指定文本的控件
    public static boolean clickText(String... texts) {
        for (String text : texts) {
            if (text == null || text.isEmpty()) continue;
            for (NodeInfo window : NodeInfo.getWindows()) {
                if (window == null || window.node == null) continue;
                List<android.view.accessibility.AccessibilityNodeInfo> nodes = window.node.findAccessibilityNodeInfosByText(text);
                if (nodes == null) continue;
                for (android.view.accessibility.AccessibilityNodeInfo node : nodes) {
                    if (node == null || !node.isVisibleToUser()) continue;
                    // 菜单项通常是可点击的叶子节点
                    if (node.isClickable() || node.getText() != null && text.contentEquals(node.getText())) {
                        if (node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
