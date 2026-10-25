package top.bogey.touch_tool.bean.pin.pin_objects;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.utils.GsonUtil;

public class PinValueArea extends PinObject {
    private int min;
    private int max;

    public PinValueArea() {
        super(PinType.VALUE_AREA);
    }

    public PinValueArea(int min, int max) {
        this();
        this.min = min;
        this.max = max;
    }

    public PinValueArea(JsonObject jsonObject) {
        super(jsonObject);
        min = GsonUtil.getAsInt(jsonObject, "min", 0);
        max = GsonUtil.getAsInt(jsonObject, "max", 0);
    }

    private void sort() {
        if (min > max) {
            int temp = min;
            min = max;
            max = temp;
        }
    }

    public int getRandomValue() {
        return min + (int) (Math.random() * (max - min));
    }

    @Override
    public void reset() {
        super.reset();
        min = 0;
        max = 0;
    }

    @Override
    public void sync(PinBase value) {
        if (value instanceof PinValueArea pinValueArea) {
            min = pinValueArea.min;
            max = pinValueArea.max;
        }
    }

    @NonNull
    @Override
    public String toString() {
        sort();
        return "(" + min + "~" + max + ")";
    }

    public int getMin() {
        sort();
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public int getMax() {
        sort();
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    @Override
    public final boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PinValueArea that)) return false;
        if (!super.equals(object)) return false;

        return getMin() == that.getMin() && getMax() == that.getMax();
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getMin();
        result = 31 * result + getMax();
        return result;
    }
}
