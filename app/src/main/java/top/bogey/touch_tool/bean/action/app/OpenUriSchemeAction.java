package top.bogey.touch_tool.bean.action.app;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

public class OpenUriSchemeAction extends ExecuteAction {
    private final transient Pin uriPin = new Pin(new PinString(), R.string.open_uri_scheme_action_uri);

    public OpenUriSchemeAction() {
        super(ActionType.OPEN_URI_SCHEME);
        addPin(uriPin);
    }

    public OpenUriSchemeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(uriPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinString uri = getPinValue(runnable, uriPin);
        AppUtil.gotoScheme(MainApplication.getInstance().getService(), uri.getValue());
        executeNext(runnable, outPin);
    }
}
