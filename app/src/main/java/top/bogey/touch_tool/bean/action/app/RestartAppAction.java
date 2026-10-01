package top.bogey.touch_tool.bean.action.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.service.super_user.SuperUser;
import top.bogey.touch_tool.utils.AppUtil;

// 重启应用：关闭应用后等待片刻再重新打开
public class RestartAppAction extends ExecuteAction {
    private final transient Pin appPin = new Pin(new PinApplication(PinSubType.SINGLE_APP_WITH_ACTIVITY), R.string.pin_app);
    private final transient Pin delayPin = new NotLinkAblePin(new PinInteger(800), R.string.restart_app_action_delay, false, false, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public RestartAppAction() {
        super(ActionType.RESTART_APP);
        addPins(appPin, delayPin, successPin, elsePin);
    }

    public RestartAppAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(appPin, delayPin, successPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinApplication app = getPinValue(runnable, appPin);
        String packageName = app.getPackageName();
        successPin.getValue(PinBoolean.class).setValue(false);

        if (packageName == null || packageName.isEmpty()) {
            executeNext(runnable, elsePin);
            return;
        }

        // 关闭（尽力而为）
        try {
            if (SuperUser.getInstance().isValid()) {
                SuperUser.getInstance().runCommand("am force-stop " + packageName);
            } else {
                Context context = MainApplication.getInstance();
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (manager != null) manager.killBackgroundProcesses(packageName);
            }
        } catch (Exception ignored) {
        }

        long delay = 800;
        if (getPinValue(runnable, delayPin) instanceof PinInteger pinInteger) {
            delay = Math.max(200, pinInteger.getValue());
        }
        runnable.sleep(delay);

        // 重新打开
        Context context = MainApplication.getInstance().getService();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            executeNext(runnable, elsePin);
            return;
        }
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AppUtil.startActivity(context, intent, null);
        successPin.getValue(PinBoolean.class).setValue(true);
        executeNext(runnable, outPin);
    }
}
