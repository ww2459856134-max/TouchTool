package top.bogey.touch_tool.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.other.run.RunResultSaver;
import top.bogey.touch_tool.bean.save.task.TaskSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.MainAccessibilityService;

// 小组件刷新工具：构建 RemoteViews、刷新单个/按任务批量刷新
public class TaskWidgetUpdater {

    private TaskWidgetUpdater() {
    }

    // 按任务刷新：该任务绑定的所有 widget 一起更新（一任务多 widget 场景）
    public static void updateByTask(Context context, String taskId) {
        if (context == null || taskId == null) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) return;
        for (int id : WidgetBindingSaver.idsOfTask(taskId)) {
            update(context, manager, id);
        }
    }

    public static void update(Context context, AppWidgetManager manager, int appWidgetId) {
        if (context == null || manager == null) return;
        RemoteViews views = buildViews(context, appWidgetId);
        manager.updateAppWidget(appWidgetId, views);
    }

    private static RemoteViews buildViews(Context context, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_task);
        WidgetBinding binding = WidgetBindingSaver.get(appWidgetId);

        // 整卡点击 = 运行/停止；显式指定组件 + FLAG_IMMUTABLE，第三方无法伪造
        Intent runIntent = new Intent(context, TaskWidgetProvider.class)
                .setAction(TaskWidgetProvider.ACTION_RUN)
                .putExtra(TaskWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent runPending = PendingIntent.getBroadcast(context, appWidgetId, runIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_root, runPending);

        // 右上角刷新按钮
        Intent refreshIntent = new Intent(context, TaskWidgetProvider.class)
                .setAction(TaskWidgetProvider.ACTION_REFRESH)
                .putExtra(TaskWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(
                context, appWidgetId + 10000, refreshIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        MainAccessibilityService service = MainApplication.getInstance().getService();
        boolean serviceOff = service == null || !service.isEnabled();

        // 未绑定：引导去配置页选择任务
        if (binding == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_unbound));
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_unbound_desc));
            views.setImageViewResource(R.id.widget_icon, R.drawable.icon_widgets);
            return views;
        }

        Task task = TaskSaver.getInstance().getTask(binding.taskId);
        // 任务已删除：提示重选
        if (task == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_task_deleted));
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_task_deleted_desc));
            views.setImageViewResource(R.id.widget_icon, R.drawable.icon_cancel);
            return views;
        }

        // 标题每次现读，任务改名后自动同步
        views.setTextViewText(R.id.widget_title, task.getTitle());

        // 运行态以无障碍服务为准，内存态只做即时反馈的补充
        boolean running = service != null && service.isTaskRunning(task);
        WidgetState.RunState state = WidgetState.get(binding.taskId);
        if (state != null && state.running) running = true;

        if (serviceOff) {
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_service_off_status));
            views.setImageViewResource(R.id.widget_icon, R.drawable.icon_play_arrow);
        } else if (running) {
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_running));
            views.setImageViewResource(R.id.widget_icon, R.drawable.icon_play_arrow);
        } else if (state != null && state.finishTime > 0) {
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(state.finishTime));
            String duration = RunResultSaver.formatDurationText(state.duration);
            if (state.stopped) {
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_stopped, time));
                views.setImageViewResource(R.id.widget_icon, R.drawable.icon_stop);
            } else if (state.success) {
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_success, duration, time));
                views.setImageViewResource(R.id.widget_icon, R.drawable.icon_check_circle);
            } else {
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_failed, duration, time));
                views.setImageViewResource(R.id.widget_icon, R.drawable.icon_cancel);
            }
        } else {
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_idle));
            views.setImageViewResource(R.id.widget_icon, R.drawable.icon_play_arrow);
        }
        return views;
    }
}
