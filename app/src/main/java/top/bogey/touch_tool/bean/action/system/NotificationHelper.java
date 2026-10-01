package top.bogey.touch_tool.bean.action.system;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import top.bogey.touch_tool.service.notification.NotificationService;

// 通知工具：从通知监听服务获取活跃通知
public class NotificationHelper {

    // 服务就绪检查：通知使用权未授权或服务未连接时返回 false
    public static boolean isServiceReady() {
        return NotificationService.getInstance() != null && NotificationService.isEnabled();
    }

    // 按包名过滤获取全部活跃通知（按发布时间从新到旧排序）
    public static List<StatusBarNotification> getNotifications(String packageFilter) {
        List<StatusBarNotification> result = new ArrayList<>();
        NotificationService service = NotificationService.getInstance();
        if (service == null || !NotificationService.isEnabled()) return result;

        StatusBarNotification[] notifications = service.getActiveNotifications();
        if (notifications == null) return result;

        for (StatusBarNotification sbn : notifications) {
            if (sbn == null || sbn.getNotification() == null) continue;
            if (packageFilter != null && !packageFilter.isEmpty() && !packageFilter.equals(sbn.getPackageName())) continue;
            result.add(sbn);
        }
        result.sort((a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return result;
    }

    // 获取最新一条匹配的通知
    public static StatusBarNotification getLatestNotification(String packageFilter) {
        List<StatusBarNotification> list = getNotifications(packageFilter);
        return list.isEmpty() ? null : list.get(0);
    }

    // 提取通知标题
    public static String getTitle(StatusBarNotification sbn) {
        CharSequence title = sbn.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
        return title == null ? "" : title.toString();
    }

    // 提取通知内容
    public static String getText(StatusBarNotification sbn) {
        CharSequence text = sbn.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        return text == null ? "" : text.toString();
    }
}
