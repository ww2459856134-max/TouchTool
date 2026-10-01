package top.bogey.touch_tool.bean.other.log;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.GsonUtil;

public class DateTimeLog extends Log {
    private long dateTime = System.currentTimeMillis();

    public DateTimeLog() {
        super(LogType.DATE_TIME);
    }

    public DateTimeLog(JsonObject jsonObject) {
        super(jsonObject);
        dateTime = GsonUtil.getAsLong(jsonObject, "dateTime", System.currentTimeMillis());
    }

    @Override
    public String getLog() {
        return AppUtil.formatDateTime(MainApplication.getInstance(), dateTime, true, true);
    }
}
