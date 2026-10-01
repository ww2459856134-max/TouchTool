package top.bogey.touch_tool.bean.action.start;

import com.google.gson.JsonObject;

import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.service.TaskRunnable;

public class NetworkStartAction extends StartAction {
    private final transient Pin statePin = new Pin(new PinList(new PinString()), R.string.network_start_action_state, true);

    public NetworkStartAction() {
        super(ActionType.NETWORK_START);
        addPin(statePin);
    }

    public NetworkStartAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(statePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        PinList states = statePin.getValue();
        List<TaskInfoSummary.NotworkState> networkState = TaskInfoSummary.getInstance().getNetworkState();
        states.clear();
        for (TaskInfoSummary.NotworkState state : networkState) {
            states.add(new PinString(state.name()));
        }
        executeNext(runnable, executePin);
    }
}
