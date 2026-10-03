package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

// 文件路径针脚：可点击按钮打开文件浏览器选择文件，返回绝对路径
public class PinFilePathString extends PinString {
    public PinFilePathString() {
        super(PinSubType.FILE_PATH);
    }

    public PinFilePathString(JsonObject jsonObject) {
        super(jsonObject);
    }
}
