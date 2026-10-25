package top.bogey.touch_tool.utils;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;

import top.bogey.touch_tool.MainApplication;

public enum EAnchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    public Point getAnchorOffset(int width, int height) {
        int x = 0, y = 0;
        switch (this) {
            case TOP_CENTER -> x = -width / 2;
            case TOP_RIGHT -> x = -width;
            case CENTER_LEFT -> y = -height / 2;
            case CENTER -> {
                x = -width / 2;
                y = -height / 2;
            }
            case CENTER_RIGHT -> {
                x = -width;
                y = -height / 2;
            }
            case BOTTOM_LEFT -> y = -height;
            case BOTTOM_CENTER -> {
                x = -width / 2;
                y = -height;
            }
            case BOTTOM_RIGHT -> {
                x = -width;
                y = -height;
            }
        }
        return new Point(x, y);
    }

    public PointF getAnchorScale() {
        float x = 0, y = 0;
        switch (this) {
            case TOP_CENTER -> x = 0.5f;
            case TOP_RIGHT -> x = 1f;
            case CENTER_LEFT -> y = 0.5f;
            case CENTER -> {
                x = 0.5f;
                y = 0.5f;
            }
            case CENTER_RIGHT -> {
                x = 1f;
                y = 0.5f;
            }
            case BOTTOM_LEFT -> y = 1f;
            case BOTTOM_CENTER -> {
                x = 0.5f;
                y = 1f;
            }
            case BOTTOM_RIGHT -> {
                x = 1f;
                y = 1f;
            }
        }
        return new PointF(x, y);
    }

    public Point getAnchorPoint(Rect area) {
        PointF scale = getAnchorScale();
        return new Point(area.left + (int) (area.width() * scale.x), area.top + (int) (area.height() * scale.y));
    }

    public Point getAnchorPoint() {
        return getAnchorPoint(DisplayUtil.getScreenArea(MainApplication.getInstance()));
    }
}
