package top.bogey.touch_tool.bean.other.log;

import com.google.gson.JsonObject;

public class NormalLog extends Log {

    public NormalLog(String log) {
        super(LogType.NORMAL);
        this.log = log;
    }

    public NormalLog(JsonObject jsonObject) {
        super(jsonObject);
    }
}
