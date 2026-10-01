package top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.pin.pin_objects.PinType;
import top.bogey.touch_tool.utils.EAnchor;
import top.bogey.touch_tool.utils.GsonUtil;

public class PinColor extends PinScaleAble<PinColor.ColorInfo> {

    public PinColor() {
        super(PinType.COLOR);
        value = new ColorInfo(Color.BLACK, 0, 0);
    }

    public PinColor(ColorInfo color) {
        this();
        value = color;
    }

    public PinColor(JsonObject jsonObject) {
        super(jsonObject);
        value = GsonUtil.getAsObject(jsonObject, "value", ColorInfo.class, new ColorInfo(Color.BLACK, 0, 0));
    }

    @Override
    public void reset() {
        super.reset();
        value = new ColorInfo(Color.BLACK, 0, 0);
    }

    @NonNull
    @Override
    public String toString() {
        if (value == null) return super.toString();
        return super.toString() + value;
    }

    @Override
    public ColorInfo getValue() {
        ColorInfo info = super.getValue();
        float scale = getScale();
        if (scale == 1) return info;
        return new ColorInfo(info.color, (int) (info.minArea * scale * scale), (int) (info.maxArea * scale * scale));
    }

    @Override
    public ColorInfo getValue(EAnchor anchor) {
        return getValue();
    }

    @Override
    public void setValue(EAnchor anchor, ColorInfo value) {
        setValue(value);
    }

    public static class ColorInfo {
        private @ColorInt int color;
        private int minArea;
        private int maxArea;

        public ColorInfo(@ColorInt int color, int minArea, int maxArea) {
            this.color = color;
            this.minArea = minArea;
            this.maxArea = maxArea;
        }

        public void setRed(int red) {
            color = Color.rgb(red, Color.green(color), Color.blue(color));
        }

        public void setGreen(int green) {
            color = Color.rgb(Color.red(color), green, Color.blue(color));
        }

        public void setBlue(int blue) {
            color = Color.rgb(Color.red(color), Color.green(color), blue);
        }

        public int getColor() {
            return color;
        }

        public String getColorString() {
            return "(" + Color.red(color) + "," + Color.green(color) + "," + Color.blue(color) + ")";
        }

        public void setColor(int color) {
            this.color = color;
        }

        public int getMinArea() {
            return minArea;
        }

        public void setMinArea(int minArea) {
            this.minArea = minArea;
        }

        public int getMaxArea() {
            return maxArea;
        }

        public void setMaxArea(int maxArea) {
            this.maxArea = maxArea;
        }

        @NonNull
        @Override
        public String toString() {
            return getColorString() + "[" + minArea + "~" + maxArea + "]";
        }

        @Override
        public final boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof ColorInfo colorInfo)) return false;

            return getColor() == colorInfo.getColor() && getMinArea() == colorInfo.getMinArea() && getMaxArea() == colorInfo.getMaxArea();
        }

        @Override
        public int hashCode() {
            int result = getColor();
            result = 31 * result + getMinArea();
            result = 31 * result + getMaxArea();
            return result;
        }
    }
}
