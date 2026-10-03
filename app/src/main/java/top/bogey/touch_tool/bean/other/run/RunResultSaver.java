package top.bogey.touch_tool.bean.other.run;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import android.graphics.Bitmap;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.service.MainAccessibilityService;

import java.io.File;
import java.io.FileOutputStream;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;

// 动作运行结果存储：内存单例，按动作ID覆盖，应用退出即清空
public class RunResultSaver {
    private volatile static RunResultSaver instance;

    private final Map<String, ActionRunResult> results = new ConcurrentHashMap<>();
    // 每个任务本轮运行的开始时间戳：用于区分"本次执行过"与"上次残留"（时间戳方案天然免疫重复 beginRun）
    private final Map<String, Long> runMarks = new ConcurrentHashMap<>();
    private final Map<String, Integer> runCounts = new ConcurrentHashMap<>();

    // 任务维度的运行统计（本会话累计）
    public static class TaskStats {
        public int runCount;
        public int executed;
        public int success;
        public int unachieved;
        public int error;
        public final Map<String, long[]> actionAgg = new ConcurrentHashMap<>(); // actionId -> [count, totalTime, maxTime]
        public final Map<String, String> actionTitles = new ConcurrentHashMap<>();
        public String lastRunTime;
    }

    private final Map<String, TaskStats> statsMap = new ConcurrentHashMap<>();
    private final Set<OnRunResultListener> listeners = ConcurrentHashMap.newKeySet();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnRunResultListener {
        void onRunResultRecorded();
    }

    // 任务每次开始运行时调用：记录本轮开始时间
    public void beginRun(String taskId) {
        runMarks.put(taskId, System.currentTimeMillis());
        runCounts.merge(taskId, 1, Integer::sum);
        getStats(taskId).runCount++;
    }

    public static String formatDurationText(long ms) {
        if (ms >= 1000) return String.format(java.util.Locale.ROOT, "%.1fs", ms / 1000.0);
        return ms + "ms";
    }

    public TaskStats getStats(String taskId) {
        return statsMap.computeIfAbsent(taskId, k -> new TaskStats());
    }

    // 失败瞬间保存屏幕快照，返回文件路径（失败时返回 null）
    public String saveFailureScreenshot(String taskId) {
        try {
            MainAccessibilityService service = MainApplication.getInstance().getService();
            if (service == null) return null;
            Bitmap bitmap = service.tryGetScreenShot();
            if (bitmap == null) return null;
            File dir = new File(MainApplication.getInstance().getFilesDir(), "run_screenshots" + File.separator + taskId);
            if (!dir.exists() && !dir.mkdirs()) return null;
            File file = new File(dir, System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    // 本轮运行开始时间（从未运行过返回 0：任何记录都不算残留，正常显示状态与耗时）
    public long getRunBeginTime(String taskId) {
        return runMarks.getOrDefault(taskId, 0L);
    }

    public void addListener(OnRunResultListener listener) {
        listeners.add(listener);
    }

    public void removeListener(OnRunResultListener listener) {
        listeners.remove(listener);
    }

    public static RunResultSaver getInstance() {
        if (instance == null) {
            synchronized (RunResultSaver.class) {
                if (instance == null) instance = new RunResultSaver();
            }
        }
        return instance;
    }

    private RunResultSaver() {
    }

    public void record(Action action, int status, long duration, String taskId, String screenshotPath) {
        try {
            ActionRunResult result = new ActionRunResult(status, duration);
            result.screenshotPath = screenshotPath;
            TaskStats stats = getStats(taskId);
            stats.executed++;
            if (status == ActionRunResult.STATUS_SUCCESS) stats.success++;
            else if (status == ActionRunResult.STATUS_UNACHIEVED) stats.unachieved++;
            else stats.error++;
            stats.lastRunTime = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            long[] agg = stats.actionAgg.computeIfAbsent(action.getId(), k -> new long[3]);
            agg[0]++;
            agg[1] += duration;
            agg[2] = Math.max(agg[2], duration);
            stats.actionTitles.put(action.getId(), action.getTitle());
            for (Pin pin : action.getPins()) {
                if (!pin.isOut() || pin.isHide()) continue;
                PinBase value = pin.getValue();
                // 二进制与列表数据太大，不进快照
                if (value instanceof PinImage || value instanceof PinList) continue;
                String text = value.toString();
                if (text == null || text.isEmpty() || "null".equals(text)) continue;
                if (text.length() > 120) text = text.substring(0, 120) + "…";
                result.outputs.add(new String[]{pin.getTitle(), text});
            }
            results.put(action.getId(), result);
            notifyListeners();
        } catch (Exception ignored) {
            // 快照失败不影响任务运行
        }
    }

    private void notifyListeners() {
        mainHandler.post(() -> listeners.forEach(l -> {
            try {
                l.onRunResultRecorded();
            } catch (Exception ignored) {
            }
        }));
    }

    @Nullable
    public ActionRunResult get(String actionId) {
        return results.get(actionId);
    }

    public void clear() {
        results.clear();
    }

    public List<String[]> getOutputs(String actionId) {
        ActionRunResult result = results.get(actionId);
        return result == null ? null : result.outputs;
    }
}
