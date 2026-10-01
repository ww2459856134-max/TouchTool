package top.bogey.touch_tool.bean.action.normal;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.float_window_manager.FloatWindow;

public class StickCloseAction extends ExecuteAction {
    private final transient Pin idPin = new Pin(new PinString(), R.string.stick_close_action_id);

    public StickCloseAction() {
        super(ActionType.CLOSE_STICK);
        addPin(idPin);
    }

    public StickCloseAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(idPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinString id = getPinValue(runnable, idPin);

        FloatWindow.dismiss(id.getValue());

        executeNext(runnable, outPin);
    }
}
