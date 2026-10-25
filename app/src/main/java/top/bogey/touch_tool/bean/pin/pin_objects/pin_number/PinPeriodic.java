package top.bogey.touch_tool.bean.pin.pin_objects.pin_number;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

public class PinPeriodic extends PinLong {
    public PinPeriodic() {
        super(PinSubType.PERIODIC);
    }

    public PinPeriodic(long value) {
        super(PinSubType.PERIODIC, value);
    }

    public PinPeriodic(JsonObject jsonObject) {
        super(jsonObject);
    }
}
