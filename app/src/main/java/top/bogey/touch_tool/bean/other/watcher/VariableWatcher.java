package top.bogey.touch_tool.bean.other.watcher;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 变量赋值历史：环形缓冲（上限200条），供变量监视器展示"谁在什么时候把变量改成了什么"
public class VariableWatcher {
    private static VariableWatcher instance;

    public static VariableWatcher getInstance() {
        synchronized (VariableWatcher.class) {
            if (instance == null) instance = new VariableWatcher();
            return instance;
        }
    }

    private VariableWatcher() {
    }

    public static class WatchEvent {
        public final long time;
        public final String varName;
        public final String oldValue;
        public final String newValue;
        public final String source;

        public WatchEvent(long time, String varName, String oldValue, String newValue, String source) {
            this.time = time;
            this.varName = varName;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.source = source;
        }
    }

    public interface OnHistoryListener {
        void onHistoryChanged();
    }

    private static final int MAX_HISTORY = 200;
    private final Deque<WatchEvent> history = new ArrayDeque<>();
    // 变量名 -> 运行时最新值文本（运行副本与编辑对象分离，实时值经赋值钩子维护）
    private final Map<String, String> latestValues = new ConcurrentHashMap<>();
    private final Set<OnHistoryListener> listeners = ConcurrentHashMap.newKeySet();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public synchronized void record(String varName, String oldValue, String newValue, String source) {
        latestValues.put(varName, newValue);
        history.addFirst(new WatchEvent(System.currentTimeMillis(), varName, oldValue, newValue, source));
        while (history.size() > MAX_HISTORY) history.removeLast();
        notifyListeners();
    }

    public synchronized List<WatchEvent> getHistory() {
        return new ArrayList<>(history);
    }

    public String getLatestValue(String varName) {
        return latestValues.get(varName);
    }

    public synchronized void clear() {
        history.clear();
        notifyListeners();
    }

    private void notifyListeners() {
        mainHandler.post(() -> listeners.forEach(l -> {
            try {
                l.onHistoryChanged();
            } catch (Exception ignored) {
            }
        }));
    }

    public void addListener(OnHistoryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(OnHistoryListener listener) {
        listeners.remove(listener);
    }

    // 敏感值掩码：变量名含密码/密钥类关键词时打码
    public static String mask(String varName, String value) {
        if (varName == null || value == null) return value;
        String lower = varName.toLowerCase();
        if (lower.contains("password") || lower.contains("pwd") || lower.contains("密码")
                || lower.contains("key") || lower.contains("token") || lower.contains("密钥")
                || lower.contains("secret") || lower.contains("令牌")) {
            return "••••••";
        }
        return value;
    }
}
