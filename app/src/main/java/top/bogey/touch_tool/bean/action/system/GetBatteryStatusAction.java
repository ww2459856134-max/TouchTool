package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.service.TaskRunnable;

public class GetBatteryStatusAction extends CalculateAction {
    private final transient Pin statusPin = new Pin(new PinSingleSelect(R.array.charging_state), R.string.get_battery_status_action_state, true);
    private final transient Pin precentPin = new Pin(new PinInteger(), R.string.get_battery_status_action_percent, true);

    public GetBatteryStatusAction() {
        super(ActionType.GET_BATTERY_STATUS);
        addPins(statusPin, precentPin);
    }

    public GetBatteryStatusAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(statusPin, precentPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        TaskInfoSummary.BatteryInfo info = TaskInfoSummary.getInstance().getBatteryInfo();
        statusPin.getValue(PinSingleSelect.class).setIndex(info.status().ordinal());
        precentPin.getValue(PinInteger.class).setValue(info.percent());
    }
}
