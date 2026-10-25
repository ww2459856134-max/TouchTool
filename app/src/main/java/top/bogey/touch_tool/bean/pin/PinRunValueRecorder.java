package top.bogey.touch_tool.bean.pin;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局运行值记录表：按 动作id + 针脚标题 记录上次运行值。
 * 运行链路和编辑器可能不是同一批 Pin 实例，所以用全局表共享记录。
 */
public class PinRunValueRecorder {
    private static final Map<String, Record> records = new HashMap<>();

    public static void record(String actionId, Pin pin, String value) {
        if (actionId == null || pin == null || value == null) return;
        synchronized (records) {
            records.put(actionId + "#" + pin.getTitle(), new Record(value, System.currentTimeMillis()));
        }
    }

    public static Record get(String actionId, Pin pin) {
        if (actionId == null || pin == null) return null;
        synchronized (records) {
            return records.get(actionId + "#" + pin.getTitle());
        }
    }

    public static class Record {
        public final String value;
        public final long time;

        Record(String value, long time) {
            this.value = value;
            this.time = time;
        }
    }
}
