package top.bogey.touch_tool.bean.pin.pin_objects;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.utils.GsonUtil;

public class PinBoolean extends PinObject {
    private boolean value;

    public PinBoolean() {
        super(PinType.BOOLEAN);
    }

    public PinBoolean(boolean value) {
        this();
        this.value = value;
    }

    public PinBoolean(JsonObject jsonObject) {
        super(jsonObject);
        value = GsonUtil.getAsBoolean(jsonObject, "value", false);
    }

    @Override
    public void reset() {
        super.reset();
        value = false;
    }

    @Override
    public void sync(PinBase value) {
        if (value instanceof PinBoolean pinBoolean) {
            this.value = pinBoolean.getValue();
        }
    }

    @Override
    public boolean cast(String value) {
        if (Boolean.TRUE.toString().equals(value)) this.value = true;
        else if (Boolean.FALSE.toString().equals(value)) this.value = false;
        else this.value = !"0".equals(value);
        return true;
    }

    @NonNull
    @Override
    public String toString() {
        return String.valueOf(value);
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    @Override
    public final boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PinBoolean that)) return false;
        if (!super.equals(object)) return false;

        return getValue() == that.getValue();
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Boolean.hashCode(getValue());
        return result;
    }
}
