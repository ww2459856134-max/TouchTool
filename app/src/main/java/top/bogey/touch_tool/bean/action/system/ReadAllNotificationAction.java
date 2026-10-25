package top.bogey.touch_tool.bean.action.system;

import android.service.notification.StatusBarNotification;

import android.widget.Toast;
import com.google.gson.JsonObject;

import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

// 读取全部通知：读取所有匹配的通知，按行输出"包名 标题: 内容"
public class ReadAllNotificationAction extends ExecuteAction {
    private final transient Pin packagePin = new NotLinkAblePin(new PinString(), R.string.notification_action_package);
    private final transient Pin countPin = new Pin(new PinInteger(0), R.string.notification_action_count, true);
    private final transient Pin textPin = new Pin(new PinString(), R.string.notification_action_all_text, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public ReadAllNotificationAction() {
        super(ActionType.NOTIFICATION_READ_ALL);
        addPins(packagePin, countPin, textPin, successPin, elsePin);
    }

    public ReadAllNotificationAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(packagePin, countPin, textPin, successPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        if (!NotificationHelper.isServiceReady()) {
            AppUtil.runOnUiThread(() -> Toast.makeText(MainApplication.getInstance(), R.string.notification_permission_tips, Toast.LENGTH_SHORT).show());
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        String filter = getPinValue(runnable, packagePin).toString();
        successPin.getValue(PinBoolean.class).setValue(false);
        countPin.getValue(PinInteger.class).setValue(0);
        textPin.setValue(new PinString(""));

        List<StatusBarNotification> notifications = NotificationHelper.getNotifications(filter);
        if (notifications.isEmpty()) {
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (StatusBarNotification sbn : notifications) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(sbn.getPackageName())
                    .append(' ').append(NotificationHelper.getTitle(sbn))
                    .append(": ").append(NotificationHelper.getText(sbn));
        }
        countPin.getValue(PinInteger.class).setValue(notifications.size());
        textPin.setValue(new PinString(builder.toString()));
        successPin.getValue(PinBoolean.class).setValue(true);
        executeNext(runnable, outPin);
    }
}
