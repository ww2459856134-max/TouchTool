package top.bogey.touch_tool.bean.action.app;

import android.app.ActivityManager;
import android.content.Context;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.service.super_user.SuperUser;

// 关闭应用：优先通过 Shell 通道强制停止，失败时尝试结束后台进程
public class CloseAppAction extends ExecuteAction {
    private final transient Pin appPin = new Pin(new PinApplication(PinSubType.SINGLE_APP_WITH_ACTIVITY), R.string.pin_app);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public CloseAppAction() {
        super(ActionType.CLOSE_APP);
        addPins(appPin, successPin, elsePin);
    }

    public CloseAppAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(appPin, successPin, elsePin);
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

        boolean result = false;
        // 优先走 Shell 通道强制停止（需要已配置 Root 或 Shizuku）
        try {
            if (SuperUser.getInstance().isValid()) {
                var cmdResult = SuperUser.getInstance().runCommand("am force-stop " + packageName);
                result = cmdResult != null && cmdResult.getResult();
            }
        } catch (Exception ignored) {
        }

        // 兜底：结束后台进程（无法完全停止前台应用）
        if (!result) {
            try {
                Context context = MainApplication.getInstance();
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (manager != null) {
                    manager.killBackgroundProcesses(packageName);
                    result = true;
                }
            } catch (Exception ignored) {
            }
        }

        successPin.getValue(PinBoolean.class).setValue(result);
        executeNext(runnable, result ? outPin : elsePin);
    }
}
