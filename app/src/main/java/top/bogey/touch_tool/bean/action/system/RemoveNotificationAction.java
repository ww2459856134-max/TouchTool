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
import top.bogey.touch_tool.service.notification.NotificationService;

// 移除通知：移除所有匹配的通知，输出移除数量
public class RemoveNotificationAction extends ExecuteAction {
    private final transient Pin packagePin = new NotLinkAblePin(new PinString(), R.string.notification_action_package);
    private final transient Pin countPin = new Pin(new PinInteger(0), R.string.notification_action_removed_count, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public RemoveNotificationAction() {
        super(ActionType.NOTIFICATION_REMOVE);
        addPins(packagePin, countPin, successPin, elsePin);
    }

    public RemoveNotificationAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(packagePin, countPin, successPin, elsePin);
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

        NotificationService service = NotificationService.getInstance();
        List<StatusBarNotification> notifications = NotificationHelper.getNotifications(filter);
        int count = 0;
        if (service != null) {
            for (StatusBarNotification sbn : notifications) {
                try {
                    // 移除通知需要通过通知监听服务本身执行
                    service.cancelNotification(sbn.getKey());
                    count++;
                } catch (Exception ignored) {
                    // 单条移除失败不影响其他通知
                }
            }
        }

        countPin.getValue(PinInteger.class).setValue(count);
        boolean result = count > 0;
        successPin.getValue(PinBoolean.class).setValue(result);
        executeNext(runnable, result ? outPin : elsePin);
    }
}
