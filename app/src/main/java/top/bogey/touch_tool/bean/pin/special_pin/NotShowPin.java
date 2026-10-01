package top.bogey.touch_tool.bean.pin.special_pin;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;

/**
 * 永不显示的Pin，用来代替删除针脚的功能
 */
public class NotShowPin extends NotLinkAblePin {

    public NotShowPin(PinBase value) {
        super(value);
    }

    public NotShowPin(PinBase value, int titleId) {
        super(value, titleId);
    }

    public NotShowPin(PinBase value, boolean out) {
        super(value, out);
    }

    public NotShowPin(PinBase value, int titleId, boolean out) {
        super(value, titleId, out);
    }

    public NotShowPin(PinBase value, int titleId, boolean out, boolean dynamic) {
        super(value, titleId, out, dynamic);
    }

    public NotShowPin(PinBase value, int titleId, boolean out, boolean dynamic, boolean hide) {
        super(value, titleId, out, dynamic, hide);
    }

    public NotShowPin(JsonObject jsonObject) {
        super(jsonObject);
    }
}
