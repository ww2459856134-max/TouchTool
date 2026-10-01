package top.bogey.touch_tool.bean.pin.pin_objects.pin_number;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

public class PinDate extends PinLong {
    public PinDate() {
        super(PinSubType.DATE, System.currentTimeMillis());
    }

    public PinDate(long value) {
        super(PinSubType.DATE, value);
    }

    public PinDate(JsonObject jsonObject) {
        super(jsonObject);
    }
}
