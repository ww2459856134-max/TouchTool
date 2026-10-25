package top.bogey.touch_tool.ui.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.other.run.RunResultSaver;
import top.bogey.touch_tool.bean.save.task.TaskSaver;
import top.bogey.touch_tool.bean.save.task.TaskSaveListener;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.ITaskListener;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.ui.InstantActivity;
import top.bogey.touch_tool.ui.MainActivity;
import top.bogey.touch_tool.utils.ThreadUtil;

// 任务桌面小组件：一键执行/停止绑定任务，显示运行态与上次结果
// 执行链路复用 InstantActivity.doAction()，与定时任务、通知按钮、tt:// 深链完全同路
public class TaskWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_RUN = "top.bogey.touch_tool.widget.RUN";
    public static final String ACTION_REFRESH = "top.bogey.touch_tool.widget.REFRESH";
    public static final String EXTRA_APPWIDGET_ID = "appWidgetId";

    // 全局任务监听：任务从任何入口（磁贴/定时/悬浮窗/通知/小组件）启动或结束都能收到
    // 静态单例持有，保证重复 addListener 是同一个对象（底层是 Set，且匿名实例无法去重）
    private static final ITaskListener TASK_LISTENER = new ITaskListener() {
        @Override
        public void onStart(TaskRunnable runnable) {
            String taskId = runnable.getStartTask().getId();
            WidgetState.markRunning(taskId, System.currentTimeMillis(),
                    RunResultSaver.getInstance().getStats(taskId).error);
            TaskWidgetUpdater.updateByTask(MainApplication.getInstance(), taskId);
        }

        @Override
        public void onFinish(TaskRunnable runnable) {
            String taskId = runnable.getStartTask().getId();
            WidgetState.finish(taskId, RunResultSaver.getInstance().getStats(taskId).error);
            TaskWidgetUpdater.updateByTask(MainApplication.getInstance(), taskId);
        }
    };

    // 任务增删改监听：任务删除后绑定失效、改名后标题同步
    private static final TaskSaveListener TASK_SAVE_LISTENER = new TaskSaveListener() {
        @Override
        public void onCreate(Task task) {
        }

        @Override
        public void onUpdate(Task task) {
            TaskWidgetUpdater.updateByTask(MainApplication.getInstance(), task.getId());
        }

        @Override
        public void onRemove(Task task) {
            TaskWidgetUpdater.updateByTask(MainApplication.getInstance(), task.getId());
        }
    };

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        registerListeners();
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        super.onUpdate(context, manager, appWidgetIds);
        // 无障碍服务可能被系统杀掉重建，每次更新都补注册（Set 去重，无副作用）
        registerListeners();
        for (int id : appWidgetIds) {
            TaskWidgetUpdater.update(context, manager, id);
        }
    }

    // 注册全局监听：服务实例和 TaskSaver 都是进程内单例，重复注册同一对象无副作用
    private void registerListeners() {
        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service != null) service.addListener(TASK_LISTENER);
        TaskSaver.getInstance().addListener(TASK_SAVE_LISTENER);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;
        String action = intent.getAction();
        int appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        if (ACTION_RUN.equals(action)) {
            handleRun(context, appWidgetId);
        } else if (ACTION_REFRESH.equals(action)) {
            TaskWidgetUpdater.update(context, AppWidgetManager.getInstance(context), appWidgetId);
        }
    }

    // 点击小组件：按绑定关系执行/停止任务，含服务未开、任务已删除、未绑定等分支
    private void handleRun(Context context, int appWidgetId) {
        WidgetBinding binding = WidgetBindingSaver.get(appWidgetId);
        if (binding == null) {
            openConfig(context, appWidgetId);
            return;
        }

        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null || !service.isEnabled()) {
            Toast.makeText(context, R.string.widget_service_off, Toast.LENGTH_SHORT).show();
            // 跳主界面引导开启无障碍服务
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        Task task = TaskSaver.getInstance().getTask(binding.taskId);
        if (task == null) {
            // 任务已删除：刷新出提示态并引导重新配置
            TaskWidgetUpdater.update(context, AppWidgetManager.getInstance(context), appWidgetId);
            openConfig(context, appWidgetId);
            return;
        }

        if (service.isTaskRunning(task)) {
            // 运行中再点 = 停止，并标记"用户停止"，onFinish 时保持已停止态
            WidgetState.markStoppedByUser(task.getId());
            service.stopTask(task);
            return;
        }

        // 先切运行中态（即时反馈），任务拷贝+提交放后台线程，避免广播超时
        WidgetState.markRunning(task.getId(), System.currentTimeMillis(),
                RunResultSaver.getInstance().getStats(task.getId()).error);
        TaskWidgetUpdater.update(context, AppWidgetManager.getInstance(context), appWidgetId);
        ThreadUtil.execute(() -> InstantActivity.doAction(binding.taskId, binding.actionId, null, null));
    }

    // 打开配置页重新选择任务
    private void openConfig(Context context, int appWidgetId) {
        Intent intent = new Intent(context, WidgetConfigActivity.class);
        intent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int id : appWidgetIds) {
            WidgetBindingSaver.remove(id);
        }
    }
}
