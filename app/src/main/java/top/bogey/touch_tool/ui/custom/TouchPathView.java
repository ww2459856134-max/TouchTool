package top.bogey.touch_tool.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinTouchPath;
import top.bogey.touch_tool.utils.DisplayUtil;

public class TouchPathView extends View {
    private final Paint paint;
    private final Point size = new Point();
    private final int lineWidth = 5;
    private List<PinTouchPath.PathPart> pathParts;

    private Collection<Path> paths = null;
    private boolean fullScreen;

    public TouchPathView(Context context) {
        this(context, (List<PinTouchPath.PathPart>) null);
    }

    public TouchPathView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(DisplayUtil.getAttrColor(context, com.google.android.material.R.attr.colorPrimaryVariant));
        paint.setStrokeWidth(lineWidth);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);

        this.pathParts = null;
        this.fullScreen = false;
    }

    public TouchPathView(Context context, List<PinTouchPath.PathPart> pathParts) {
        this(context, pathParts, false);
    }

    public TouchPathView(Context context, List<PinTouchPath.PathPart> pathParts, boolean fullScreen) {
        super(context, null);
        paint = new Paint();
        paint.setColor(DisplayUtil.getAttrColor(context, com.google.android.material.R.attr.colorPrimaryVariant));
        paint.setStrokeWidth(lineWidth);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);

        this.pathParts = pathParts;
        this.fullScreen = fullScreen;
    }

    public void setPath(List<PinTouchPath.PathPart> pathParts) {
        fullScreen = false;
        if (size.x == 0 && size.y == 0) {
            this.pathParts = pathParts;
        } else {
            formatPath(pathParts);
            postInvalidate();
        }
    }

    public void setFullPath(List<PinTouchPath.PathPart> pathParts) {
        fullScreen = true;
        if (size.x == 0 && size.y == 0) {
            this.pathParts = pathParts;
        } else {
            formatPath(pathParts);
            postInvalidate();
        }
    }

    private void formatPath(List<PinTouchPath.PathPart> pathParts) {
        Rect area;
        if (fullScreen) {
            area = DisplayUtil.getScreenArea(getContext());
        } else {
            List<Point> points = new ArrayList<>();
            for (PinTouchPath.PathPart part : pathParts) {
                points.addAll(part.getPoints());
            }
            area = DisplayUtil.getPointsArea(points);
        }

        float xScale, yScale;
        if (area.width() == 0) xScale = 1;
        else xScale = size.x * 1f / area.width();
        if (area.height() == 0) yScale = 1;
        else yScale = size.y * 1f / area.height();

        float scale, xOffset, yOffset;
        scale = Math.min(xScale, yScale);
        xOffset = (size.x - area.width() * scale) / 2;
        yOffset = (size.y - area.height() * scale) / 2;

        Map<Integer, Path> pathMap = new HashMap<>();
        for (PinTouchPath.PathPart part : pathParts) {
            PinTouchPath.PathPart copy = new PinTouchPath.PathPart(part);
            copy.offset(-area.left, -area.top);
            copy.scale(scale);
            copy.offset((int) xOffset, (int) yOffset);

            for (PinTouchPath.PathPoint point : copy.getPoints()) {
                Path path = pathMap.get(point.getId());
                if (path == null) {
                    path = new Path();
                    pathMap.put(point.getId(), path);
                    path.moveTo(point.x, point.y);
                } else {
                    path.lineTo(point.x, point.y);
                }
            }
        }

        paths = pathMap.values();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        size.set(getWidth() - lineWidth * 2, getHeight() - lineWidth * 2);
        if (pathParts != null) formatPath(pathParts);
        if (changed) postInvalidate();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        super.draw(canvas);

        if (paths == null) return;
        for (Path path : paths) {
            canvas.drawPath(path, paint);
        }

        if (fullScreen) {
            float px = DisplayUtil.dp2px(getContext(), 28);
            paint.setAlpha(128);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), px, px, paint);
        }
    }
}
