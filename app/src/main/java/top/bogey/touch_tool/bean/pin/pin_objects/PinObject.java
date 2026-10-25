package top.bogey.touch_tool.bean.pin.pin_objects;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

public class PinObject extends PinBase {

    public PinObject() {
        super(PinType.OBJECT);
    }

    protected PinObject(PinType type) {
        super(type);
    }

    public PinObject(PinSubType subType) {
        super(PinType.OBJECT, subType);
    }

    protected PinObject(PinType type, PinSubType subType) {
        super(type, subType);
    }

    public PinObject(JsonObject jsonObject) {
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
        return getSubType() == PinSubType.DYNAMIC;
    }

    @Override
    public boolean linkFromAble(PinBase pin) {
        if (getType().getGroup() == pin.getType().getGroup()) {
            if (isDynamic() || pin.isDynamic()) return true;
            return super.linkFromAble(pin);
        }
        return false;
    }

    @Override
    public boolean linkToAble(PinBase pin) {
        if (getType().getGroup() == pin.getType().getGroup()) {
            if (isDynamic() || pin.isDynamic()) return true;
            return super.linkToAble(pin);
        }
        return false;
    }

    public boolean cast(String value) {
        return false;
    }

    @NonNull
    @Override
    public String toString() {
        return "";
    }
}
