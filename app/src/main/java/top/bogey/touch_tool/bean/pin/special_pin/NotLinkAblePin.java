package top.bogey.touch_tool.bean.pin.special_pin;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;

public class NotLinkAblePin extends Pin {

    public NotLinkAblePin(JsonObject jsonObject) {
        super(jsonObject);
    }

    public NotLinkAblePin(PinBase value) {
        super(value);
    }

    public NotLinkAblePin(PinBase value, boolean out) {
        super(value, out);
    }

    public NotLinkAblePin(PinBase value, int titleId) {
        super(value, titleId);
    }

    public NotLinkAblePin(PinBase value, int titleId, boolean out) {
        super(value, titleId, out);
    }

    public NotLinkAblePin(PinBase value, int titleId, boolean out, boolean dynamic) {
        super(value, titleId, out, dynamic);
    }

    public NotLinkAblePin(PinBase value, int titleId, boolean out, boolean dynamic, boolean hide) {
        super(value, titleId, out, dynamic, hide);
    }

    @Override
    public boolean linkAble() {
        return false;
    }
}
