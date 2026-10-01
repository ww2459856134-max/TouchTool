package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

public class PinFileContentString extends PinString {

    public PinFileContentString() {
        super(PinSubType.FILE_CONTENT);
    }

    public PinFileContentString(String str) {
        super(PinSubType.FILE_CONTENT, str);
    }

    public PinFileContentString(JsonObject jsonObject) {
        super(jsonObject);
    }
}
