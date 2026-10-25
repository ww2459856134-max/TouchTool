package top.bogey.touch_tool.bean.pin.pin_objects.pin_execute;

import com.google.gson.JsonObject;

import java.util.Objects;

import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.utils.GsonUtil;

public class PinStringExecute extends PinExecute {
    private String value;

    public PinStringExecute() {
        super(PinSubType.WITH_STRING);
    }

    public PinStringExecute(PinSubType subType) {
        super(subType);
    }

    public PinStringExecute(JsonObject jsonObject) {
        super(jsonObject);
        value = GsonUtil.getAsString(jsonObject, "value", null);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public void reset() {
        super.reset();
        value = null;
    }

    @Override
    public void sync(PinBase value) {
        if (value instanceof PinStringExecute pinString) {
            this.value = pinString.value;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PinStringExecute that)) return false;
        if (!super.equals(object)) return false;

        return Objects.equals(getValue(), that.getValue());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(getValue());
        return result;
    }
}
