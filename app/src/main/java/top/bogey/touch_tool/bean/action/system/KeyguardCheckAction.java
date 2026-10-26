package top.bogey.touch_tool.bean.action.system;

import android.app.KeyguardManager;
import android.content.Context;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.service.TaskRunnable;

// 是否锁屏：输出当前屏幕是否处于锁屏状态，连线给条件分支走不同流程
public class KeyguardCheckAction extends ExecuteAction {
    private final transient Pin lockedPin = new Pin(new PinBoolean(), R.string.keyguard_check_action_result, true);

    public KeyguardCheckAction() {
        super(ActionType.KEYGUARD_CHECK);
        addPin(lockedPin);
    }

    public KeyguardCheckAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(lockedPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        Context context = MainApplication.getInstance();
        KeyguardManager manager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = manager != null && manager.isKeyguardLocked();
        lockedPin.getValue(PinBoolean.class).setValue(locked);
        executeNext(runnable, outPin);
    }
}
