package top.bogey.touch_tool.bean.action.list;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.service.TaskRunnable;

public class ListIsEmptyAction extends ListCalculateAction {
    private final transient Pin listPin = new Pin(new PinList());
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);

    public ListIsEmptyAction() {
        super(ActionType.LIST_IS_EMPTY);
        addPins(listPin, resultPin);
    }

    public ListIsEmptyAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(listPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinList list = getPinValue(runnable, listPin);
        resultPin.getValue(PinBoolean.class).setValue(list.isEmpty());
    }

    @NonNull
    @Override
    public List<Pin> getDynamicTypePins() {
        return Collections.singletonList(listPin);
    }

}
