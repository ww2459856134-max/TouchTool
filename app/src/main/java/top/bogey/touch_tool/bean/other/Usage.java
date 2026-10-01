package top.bogey.touch_tool.bean.other;

import android.graphics.Point;

import java.util.List;

import top.bogey.touch_tool.bean.task.Task;

public record Usage(Task task, List<Point> points) {
}
