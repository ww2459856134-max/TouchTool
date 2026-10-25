package top.bogey.touch_tool.ui.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.start.StartAction;
import top.bogey.touch_tool.bean.save.task.TaskSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.databinding.ActivityWidgetConfigBinding;
import top.bogey.touch_tool.ui.BaseActivity;

// 小组件配置页：添加 widget 时弹出，选择要绑定的任务
// 只列出有可用触发器（已启用且非定时）的任务，避免选出点了没反应的任务
public class WidgetConfigActivity extends BaseActivity {
    public static final String EXTRA_APPWIDGET_ID = TaskWidgetProvider.EXTRA_APPWIDGET_ID;

    private ActivityWidgetConfigBinding binding;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWidgetConfigBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        appWidgetId = getIntent().getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        // 系统要求：配置页必须先以 RESULT_CANCELED + EXTRA_APPWIDGET_ID 占位，
        // 否则用户按返回键时 widget 会添加异常
        setResult(RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId));

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        binding.taskList.setLayoutManager(new LinearLayoutManager(this));
        List<WidgetTaskAdapter.Item> items = buildItems();
        binding.taskList.setAdapter(new WidgetTaskAdapter(items, this::onTaskSelected));
        binding.emptyView.setVisibility(items.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    // 组装可绑定的任务列表：附带为该任务选定的触发器 id
    private List<WidgetTaskAdapter.Item> buildItems() {
        List<WidgetTaskAdapter.Item> items = new ArrayList<>();
        for (Task task : TaskSaver.getInstance().getOrderTasks()) {
            String actionId = pickStartAction(task);
            if (actionId != null) {
                items.add(new WidgetTaskAdapter.Item(task, actionId));
            }
        }
        return items;
    }

    // 选哪个触发器：优先"手动执行"；否则取第一个启用中的非定时触发器
    // （定时触发器不能绑 widget——doAction 对它的处理是设闹钟而不是立即执行）
    private String pickStartAction(Task task) {
        String fallback = null;
        for (Action action : task.getActions(StartAction.class)) {
            if (!(action instanceof StartAction startAction) || !startAction.isEnable()) continue;
            if (action.getType() == ActionType.MANUAL_START) return action.getId();
            if (fallback == null && action.getType() != ActionType.TIME_START) fallback = action.getId();
        }
        return fallback;
    }

    // 选中任务：保存绑定、刷新 widget、按系统约定回 RESULT_OK
    private void onTaskSelected(WidgetTaskAdapter.Item item) {
        WidgetBindingSaver.save(appWidgetId, item.task.getId(), item.actionId);
        TaskWidgetUpdater.update(this, AppWidgetManager.getInstance(this), appWidgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId));
        finish();
    }
}
