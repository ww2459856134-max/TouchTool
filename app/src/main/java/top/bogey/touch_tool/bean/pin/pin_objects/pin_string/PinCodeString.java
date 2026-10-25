package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

// 多行代码文本针脚，用于编辑 JavaScript 等代码
public class PinCodeString extends PinString {
    public PinCodeString() {
        super(PinSubType.CODE_TEXT);
    }

    public PinCodeString(JsonObject jsonObject) {
        super(jsonObject);
    }
}
