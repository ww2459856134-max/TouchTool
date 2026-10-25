package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

// 目录路径针脚：点击按钮打开文件浏览器选择目录，返回绝对路径
public class PinDirPathString extends PinString {
    public PinDirPathString() {
        super(PinSubType.DIR_PATH);
    }

    public PinDirPathString(JsonObject jsonObject) {
        super(jsonObject);
    }
}
