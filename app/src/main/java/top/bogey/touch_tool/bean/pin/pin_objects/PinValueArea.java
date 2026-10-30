package top.bogey.touch_tool.bean.pin.pin_objects;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.utils.GsonUtil;

public class PinValueArea extends PinObject {
    private int min;
    private int max;
    // 区间插值表达式：如 {延迟范围} 或 {最小}~{最大}，运行时插值后解析；空表示纯数值区间
    private String expression;

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
            expression = pinValueArea.expression;
        }
    }

    // 解析插值结果：支持 "min~max"、"min,max"、"min max" 与单值（单值时 min=max）；成功返回 true
    public boolean parse(String text) {
        if (text == null) return false;
        String s = text.replace("(", "").replace(")", "").trim();
        if (s.isEmpty()) return false;
        try {
            String[] parts = s.split("[~，,\\s]+");
            if (parts.length == 1) {
                min = Integer.parseInt(parts[0].trim());
                max = min;
            } else if (parts.length >= 2) {
                min = Integer.parseInt(parts[0].trim());
                max = Integer.parseInt(parts[1].trim());
            } else {
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
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

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
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
