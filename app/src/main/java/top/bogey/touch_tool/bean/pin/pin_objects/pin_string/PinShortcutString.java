package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

public class PinShortcutString extends PinString {

    public PinShortcutString() {
        super(PinSubType.SHORTCUT);
    }

    public PinShortcutString(String str) {
        super(PinSubType.SHORTCUT, str);
    }

    public PinShortcutString(JsonObject jsonObject) {
        super(jsonObject);
    }
}
