package top.bogey.touch_tool.utils;

import android.graphics.Rect;

public class MatchResult {
    public double value;
    public Rect area;

    public MatchResult(double value, int x, int y, int width, int height) {
        this.value = value;
        this.area = new Rect(x, y, x + width, y + height);
    }
}
