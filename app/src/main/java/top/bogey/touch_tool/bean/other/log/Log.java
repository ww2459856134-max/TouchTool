package top.bogey.touch_tool.bean.other.log;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

import top.bogey.touch_tool.utils.GsonUtil;

public abstract class Log {
    protected final LogType type;
    protected String log;

    public Log(LogType type) {
        this.type = type;
    }

    public Log(JsonObject jsonObject) {
        type = GsonUtil.getAsObject(jsonObject, "type", LogType.class, LogType.NORMAL);
        log = GsonUtil.getAsString(jsonObject, "log", "");
    }

    public LogType getType() {
        return type;
    }

    public String getLog() {
        return log;
    }

    public static class LogDeserialize implements JsonDeserializer<Log> {
        @Override
        public Log deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            LogType type = GsonUtil.getAsObject(jsonObject, "type", LogType.class, LogType.NORMAL);
            Log log = null;
            switch (type) {
                case NORMAL -> log = new NormalLog(jsonObject);
                case ACTION -> log = new ActionLog(jsonObject);
                case DATE_TIME -> log = new DateTimeLog(jsonObject);
            }
            return log;
        }
    }
}
