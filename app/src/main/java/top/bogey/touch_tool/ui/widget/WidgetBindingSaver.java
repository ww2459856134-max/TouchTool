package top.bogey.touch_tool.ui.widget;

import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.utils.GsonUtil;

// 桌面小组件绑定存储：appWidgetId -> 任务/触发器，MMKV 持久化（与 TaskSaver 同款写法）
public class WidgetBindingSaver {
    private static final MMKV mmkv = MMKV.mmkvWithID("WIDGET_DB", MMKV.SINGLE_PROCESS_MODE);

    private WidgetBindingSaver() {
    }

    // 保存绑定；taskId/actionId 来自配置页选择
    public static void save(int appWidgetId, String taskId, String actionId) {
        WidgetBinding binding = new WidgetBinding();
        binding.taskId = taskId;
        binding.actionId = actionId;
        mmkv.encode(String.valueOf(appWidgetId), GsonUtil.toJson(binding));
    }

    public static WidgetBinding get(int appWidgetId) {
        String json = mmkv.decodeString(String.valueOf(appWidgetId));
        if (json == null || json.isEmpty()) return null;
        return GsonUtil.getAsObject(json, WidgetBinding.class, null);
    }

    public static void remove(int appWidgetId) {
        mmkv.remove(String.valueOf(appWidgetId));
    }

    // 某任务绑定的所有 widgetId（多个 widget 绑同一任务时批量刷新用）
    public static List<Integer> idsOfTask(String taskId) {
        List<Integer> ids = new ArrayList<>();
        String[] keys = mmkv.allKeys();
        if (keys == null) return ids;
        for (String key : keys) {
            WidgetBinding binding = GsonUtil.getAsObject(mmkv.decodeString(key), WidgetBinding.class, null);
            if (binding != null && taskId.equals(binding.taskId)) {
                try {
                    ids.add(Integer.parseInt(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ids;
    }
}
