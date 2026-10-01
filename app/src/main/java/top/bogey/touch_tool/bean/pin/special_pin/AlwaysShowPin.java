package top.bogey.touch_tool.bean.pin.special_pin;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;

public class AlwaysShowPin extends NotLinkAblePin {

    public AlwaysShowPin(PinBase value) {
        super(value);
    }

    public AlwaysShowPin(PinBase value, int titleId) {
        super(value, titleId);
    }

    public AlwaysShowPin(PinBase value, boolean out) {
        super(value, out);
    }

    public AlwaysShowPin(PinBase value, int titleId, boolean out) {
        super(value, titleId, out);
    }

    public AlwaysShowPin(PinBase value, int titleId, boolean out, boolean dynamic) {
        super(value, titleId, out, dynamic);
    }

    public AlwaysShowPin(PinBase value, int titleId, boolean out, boolean dynamic, boolean hide) {
        super(value, titleId, out, dynamic, hide);
    }

    public AlwaysShowPin(JsonObject jsonObject) {
        super(jsonObject);
    }
}
