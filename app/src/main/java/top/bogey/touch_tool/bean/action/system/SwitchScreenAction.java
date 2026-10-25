package top.bogey.touch_tool.bean.action.system;

import android.os.Build;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

public class SwitchScreenAction extends ExecuteAction {
    private final transient Pin screenPin = new Pin(new PinBoolean(true), R.string.switch_screen_action_switch);

    public SwitchScreenAction() {
        super(ActionType.SWITCH_SCREEN);
        addPin(screenPin);
    }

    public SwitchScreenAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(screenPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinBoolean screen = getPinValue(runnable, screenPin);
        if (screen.getValue()) {
            AppUtil.wakePhone(MainApplication.getInstance());
        } else {
            MainAccessibilityService service = MainApplication.getInstance().getService();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(MainAccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
            }
        }
        executeNext(runnable, outPin);
    }
}
