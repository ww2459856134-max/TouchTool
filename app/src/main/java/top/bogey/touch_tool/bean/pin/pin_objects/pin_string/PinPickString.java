package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.utils.GsonUtil;

// 可通过控件拾取填充的文本针脚
// pickType：0 文本，1 ID，2 类名，3 描述
public class PinPickString extends PinString {
    private int pickType;

    public PinPickString(int pickType) {
        super(PinSubType.PICK_STRING);
        this.pickType = pickType;
    }

    public PinPickString(JsonObject jsonObject) {
        super(jsonObject);
        pickType = GsonUtil.getAsInt(jsonObject, "pickType", 0);
    }

    public int getPickType() {
        return pickType;
    }
}
