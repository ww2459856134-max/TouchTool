package top.bogey.touch_tool.bean.pin;

import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.task.Task;

public interface PinListener {
    void onLinkedTo(Task task, Pin origin, Pin to);

    void onUnLinkedFrom(Task task, Pin origin, Pin from);

    void onValueReplaced(Task task, Pin origin, PinBase value);

    void onValueUpdated(Task task, Pin origin, PinBase value);

    void onTitleChanged(Pin origin, String title);
}
