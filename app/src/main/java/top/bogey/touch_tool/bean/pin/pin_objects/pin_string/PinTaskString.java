package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;

public class PinTaskString extends PinString {

    public PinTaskString() {
        super(PinSubType.TASK_ID);
    }

    public PinTaskString(PinSubType subType) {
        super(subType);
    }

    public PinTaskString(String value) {
        super(PinSubType.TASK_ID, value);
    }

    public PinTaskString(JsonObject jsonObject) {
        super(jsonObject);
    }
}
