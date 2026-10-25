package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.service.TaskRunnable;

public class GetNetworkStatusAction extends CalculateAction {
    private final transient Pin statusPin = new Pin(new PinList(new PinString()), R.string.get_network_status_action_state, true);

    public GetNetworkStatusAction() {
        super(ActionType.GET_NETWORK_STATUS);
        addPin(statusPin);
    }

    public GetNetworkStatusAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(statusPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinList states = statusPin.getValue(PinList.class);
        List<TaskInfoSummary.NotworkState> state = TaskInfoSummary.getInstance().getNetworkState();
        for (TaskInfoSummary.NotworkState notworkState : state) {
            states.add(new PinString(notworkState.name()));
        }
    }
}
