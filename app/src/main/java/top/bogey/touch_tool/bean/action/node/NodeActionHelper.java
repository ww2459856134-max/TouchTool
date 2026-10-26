package top.bogey.touch_tool.bean.action.node;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

// 控件操作降级助手：无障碍操作不被支持时，用手势方式兜底
public class NodeActionHelper {

    // 在控件区域内按九宫格位置取坐标
    // 顺序与 touch_pos_type 数组一致：0 中心，1 左上，2 上中，3 右上，4 左中，5 右中，6 左下，7 下中，8 右下
    private static final float[] POS_X = {0.5f, 0.25f, 0.5f, 0.75f, 0.25f, 0.75f, 0.25f, 0.5f, 0.75f};
    private static final float[] POS_Y = {0.5f, 0.25f, 0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f, 0.75f};

    // 手势点击：支持九宫格定位、自定义按住时长、连点与间隔
    // holdMs 为 0 时普通点击用 60ms，长按场景由调用方传入 600
    // count 为连点次数，intervalMs 为两次之间的间隔
    public static boolean gestureClick(NodeInfo nodeInfo, int posIndex, long holdMs, int count, long intervalMs, TaskRunnable runnable) {
        if (nodeInfo == null || nodeInfo.area == null) return false;
        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null || !service.isEnabled()) return false;

        Rect area = nodeInfo.area;
        if (area.width() <= 0 || area.height() <= 0) return false;

        int index = posIndex >= 0 && posIndex < POS_X.length ? posIndex : 0;
        float x = area.left + area.width() * POS_X[index];
        float y = area.top + area.height() * POS_Y[index];
        long duration = holdMs > 0 ? holdMs : 60;
        int times = Math.max(1, count);

        for (int i = 0; i < times; i++) {
            // 连点间隔：从第二次开始，每次点击前等待（可被任务停止打断）
            if (i > 0 && intervalMs > 0 && runnable != null) runnable.sleep(intervalMs);

            Path path = new Path();
            path.moveTo(x, y);
            CountDownLatch latch = new CountDownLatch(1);
            boolean[] ok = {false};
            service.runGesture(Collections.singleton(
                    new GestureDescription.StrokeDescription(path, 0, duration)), result -> {
                ok[0] = result;
                latch.countDown();
            });
            try {
                if (!latch.await(duration + 1000, TimeUnit.MILLISECONDS)) return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (!ok[0]) return false;

            // 等界面消化手势
            if (runnable != null) runnable.sleep(100);
        }
        return true;
    }

    // 点一下控件中心点，用于无障碍聚焦/点击失败时兜底
    // runnable 可传 null：传了就用可中断的 sleep，避免任务停止后线程仍被挂住
    public static boolean tapCenter(NodeInfo nodeInfo, TaskRunnable runnable) {
        if (nodeInfo == null || nodeInfo.area == null) return false;
        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null || !service.isEnabled()) return false;

        Rect area = nodeInfo.area;
        if (area.width() <= 0 || area.height() <= 0) return false;

        Path path = new Path();
        path.moveTo(area.centerX(), area.centerY());

        CountDownLatch latch = new CountDownLatch(1);
        service.runGesture(Collections.singleton(
                new GestureDescription.StrokeDescription(path, 0, 60)), result -> latch.countDown());
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        // 等界面消化手势；有 runnable 时走 100ms 切片版本，可被任务停止打断
        if (runnable != null) runnable.sleep(150);
        return true;
    }

    // 长按控件中心点，唤出文本选择菜单
    public static boolean longPress(NodeInfo nodeInfo) {
        if (nodeInfo == null || nodeInfo.area == null) return false;
        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null) return false;

        android.graphics.Rect area = nodeInfo.area;
        Path path = new Path();
        path.moveTo(area.centerX(), area.centerY());

        CountDownLatch latch = new CountDownLatch(1);
        service.runGesture(Collections.singleton(
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
