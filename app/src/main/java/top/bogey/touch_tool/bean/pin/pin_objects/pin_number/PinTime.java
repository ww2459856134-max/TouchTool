package top.bogey.touch_tool.bean.pin.pin_objects.pin_number;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

public class PinTime extends PinLong {

    public PinTime() {
        super(PinSubType.TIME, formatTime(System.currentTimeMillis()));
    }

    public PinTime(long value) {
        super(PinSubType.TIME, formatTime(value));
    }

    public PinTime(JsonObject jsonObject) {
        super(jsonObject);
    }

    private static long formatTime(long time) {
        final long MIN_TIME = 60 * 1000;
        return time / MIN_TIME * MIN_TIME;
    }
}
