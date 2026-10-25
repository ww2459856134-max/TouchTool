package top.bogey.touch_tool.bean.action.start;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.service.TaskRunnable;

public class ReceivedShareStartAction extends StartAction {

    public ReceivedShareStartAction() {
        super(ActionType.RECEIVED_SHARE_START);
    }

    public ReceivedShareStartAction(JsonObject jsonObject) {
        super(jsonObject);
        tmpPins.forEach(this::addPin);
        tmpPins.clear();
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        executeNext(runnable, executePin);
    }
}