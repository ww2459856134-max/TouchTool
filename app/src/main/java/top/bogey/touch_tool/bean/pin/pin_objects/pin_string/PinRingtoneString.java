package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

public class PinRingtoneString extends PinString {

    public PinRingtoneString() {
        super(PinSubType.RINGTONE);
    }

    public PinRingtoneString(String str) {
        super(PinSubType.RINGTONE, str);
    }

    public PinRingtoneString(JsonObject jsonObject) {
        super(jsonObject);
    }
}
