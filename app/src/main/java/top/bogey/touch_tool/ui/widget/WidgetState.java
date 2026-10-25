package top.bogey.touch_tool.ui.widget;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 小组件运行态：仅存内存，进程被杀后自然清空（widget 回落到空闲态，无副作用）
// 错误基线机制：TaskStats.error 是会话累计值，onStart 记基线、onFinish 作差，才能回答"本次有没有错误"
public class WidgetState {
    private static final Map<String, RunState> STATES = new ConcurrentHashMap<>();    // taskId -> 运行态
    private static final Map<String, Integer> ERROR_BASE = new ConcurrentHashMap<>(); // taskId -> onStart 时的累计错误数

    private WidgetState() {
    }

    public static class RunState {
        public boolean running;
        public long startTime;
        public long duration;    // 上次耗时 ms
        public boolean success;  // 上次是否无错误
        public boolean stopped;  // 上次是否被用户停止
        public long finishTime;
    }

    // 任务启动：记录错误基线与开始时间
    public static void markRunning(String taskId, long startTime, int errorBase) {
        ERROR_BASE.put(taskId, errorBase);
        RunState state = STATES.computeIfAbsent(taskId, k -> new RunState());
        state.running = true;
        state.startTime = startTime;
        state.stopped = false;
    }

    // 用户主动停止：onFinish 到达时保持"已停止"态，不误报成功
    public static void markStoppedByUser(String taskId) {
        RunState state = STATES.get(taskId);
        if (state != null) state.stopped = true;
    }

    // 任务结束：与基线比较得出本次是否有错误
    public static RunState finish(String taskId, int errorNow) {
        RunState state = STATES.computeIfAbsent(taskId, k -> new RunState());
        Integer base = ERROR_BASE.remove(taskId);
        state.running = false;
        // 没收到过 onStart（进程中途被杀又重建）时 startTime 为 0，耗时置 0 避免算出天文数字
        state.duration = state.startTime > 0 ? System.currentTimeMillis() - state.startTime : 0;
        // 没有基线时保守判定：本次会话零错误才算成功
        state.success = base != null ? errorNow <= base : errorNow == 0;
        state.finishTime = System.currentTimeMillis();
        return state;
    }

    public static RunState get(String taskId) {
        return STATES.get(taskId);
    }
}
