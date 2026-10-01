package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

public class PinSingleLineString extends PinString {

    public PinSingleLineString() {
        super(PinSubType.SINGLE_LINE);
    }

    public PinSingleLineString(String str) {
        super(PinSubType.SINGLE_LINE, str);
    }

    public PinSingleLineString(JsonObject jsonObject) {
        super(jsonObject);
    }
}
