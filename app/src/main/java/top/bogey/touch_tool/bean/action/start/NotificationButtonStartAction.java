package top.bogey.touch_tool.bean.action.start;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleLineString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

// 通知栏按钮触发器：点击常驻通知上的按钮时执行
public class NotificationButtonStartAction extends StartAction {
    private final transient Pin namePin = new NotLinkAblePin(new PinSingleLineString(), R.string.notification_button_start_action_name);

    public NotificationButtonStartAction() {
        super(ActionType.NOTIFICATION_BUTTON_START);
        addPin(namePin);
    }

    public NotificationButtonStartAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(namePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        executeNext(runnable, executePin);
    }

    public String getName() {
        return namePin.getValue(PinSingleLineString.class).getValue();
    }
}
