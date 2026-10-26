package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.utils.GsonUtil;

// 查找控件附加条件针脚：value 存条件行列表的 JSON（NodeCondition.listToJson）
public class PinConditionsString extends PinString {
    public PinConditionsString() {
        super(PinSubType.CONDITIONS);
    }

    public PinConditionsString(String value) {
        super(PinSubType.CONDITIONS);
        this.value = value;
    }

    public PinConditionsString(JsonObject jsonObject) {
        super(jsonObject);
        value = GsonUtil.getAsString(jsonObject, "value", null);
    }
}
