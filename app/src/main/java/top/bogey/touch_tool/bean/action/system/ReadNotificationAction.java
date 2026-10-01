package top.bogey.touch_tool.bean.action.system;

import android.widget.Toast;
import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

// 读取最新通知：读取最新一条匹配通知的来源、标题与内容
public class ReadNotificationAction extends ExecuteAction {
    private final transient Pin packagePin = new NotLinkAblePin(new PinString(), R.string.notification_action_package);
    private final transient Pin fromPin = new Pin(new PinString(), R.string.notification_action_from, true);
    private final transient Pin titlePin = new Pin(new PinString(), R.string.notification_action_title, true);
    private final transient Pin textPin = new Pin(new PinString(), R.string.notification_action_text, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public ReadNotificationAction() {
        super(ActionType.NOTIFICATION_READ);
        addPins(packagePin, fromPin, titlePin, textPin, successPin, elsePin);
    }

    public ReadNotificationAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(packagePin, fromPin, titlePin, textPin, successPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        if (!NotificationHelper.isServiceReady()) {
            AppUtil.runOnUiThread(() -> Toast.makeText(MainApplication.getInstance(), R.string.notification_permission_tips, Toast.LENGTH_SHORT).show());
            executeNext(runnable, elsePin);
            return;
        }

        String filter = getPinValue(runnable, packagePin).toString();
        successPin.getValue(PinBoolean.class).setValue(false);
        fromPin.setValue(new PinString(""));
        titlePin.setValue(new PinString(""));
        textPin.setValue(new PinString(""));

        var sbn = NotificationHelper.getLatestNotification(filter);
        boolean result = sbn != null;
        if (result) {
            fromPin.setValue(new PinString(sbn.getPackageName()));
            titlePin.setValue(new PinString(NotificationHelper.getTitle(sbn)));
            textPin.setValue(new PinString(NotificationHelper.getText(sbn)));
            successPin.getValue(PinBoolean.class).setValue(true);
        }
        executeNext(runnable, result ? outPin : elsePin);
    }
}
