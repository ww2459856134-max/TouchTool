package top.bogey.touch_tool.bean.action.start;

import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.service.TaskRunnable;

public class InnerStartAction extends StartAction {
    private final transient Pin startPin;

    public InnerStartAction(Pin startPin) {
        super(ActionType.INNER_START);
        this.startPin = startPin;
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        executeNext(runnable, startPin);
    }

    @Override
    public RestartType getRestartType() {
        return RestartType.NEW;
    }
}
