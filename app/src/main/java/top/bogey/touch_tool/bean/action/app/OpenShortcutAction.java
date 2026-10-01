package top.bogey.touch_tool.bean.action.app;

import android.content.Context;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

public class OpenShortcutAction extends ExecuteAction {
    private final transient Pin appPin = new NotLinkAblePin(new PinApplication(PinSubType.SINGLE_SHORTCUT_ACTIVITY), R.string.pin_app);

    public OpenShortcutAction() {
        super(ActionType.OPEN_SHORTCUT);
        addPin(appPin);
    }

    public OpenShortcutAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(appPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinApplication app = getPinValue(runnable, appPin);
        String intent = app.getFirstActivity();
        if (intent != null) {
            Context context = MainApplication.getInstance().getService();
            AppUtil.gotoScheme(context, intent);
        }
        executeNext(runnable, outPin);
    }
}
