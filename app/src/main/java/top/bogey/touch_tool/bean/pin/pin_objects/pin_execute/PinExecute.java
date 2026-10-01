package top.bogey.touch_tool.bean.pin.pin_objects.pin_execute;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.PinType;

public class PinExecute extends PinBase {

    public PinExecute() {
        super(PinType.EXECUTE);
    }

    protected PinExecute(PinSubType subType) {
        super(PinType.EXECUTE, subType);
    }

    public PinExecute(JsonObject jsonObject) {
        super(jsonObject);
    }

    @Override
    public void reset() {

    }

    @Override
    public void sync(PinBase value) {

    }

    @Override
    public boolean isDynamic() {
        return false;
    }
}
