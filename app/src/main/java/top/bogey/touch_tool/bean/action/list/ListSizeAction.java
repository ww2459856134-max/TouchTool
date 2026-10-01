package top.bogey.touch_tool.bean.action.list;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.service.TaskRunnable;

public class ListSizeAction extends ListCalculateAction {
    private final transient Pin listPin = new Pin(new PinList());
    private final transient Pin sizePin = new Pin(new PinInteger(), R.string.pin_number_integer, true);

    public ListSizeAction() {
        super(ActionType.LIST_SIZE);
        addPins(listPin, sizePin);
    }

    public ListSizeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(listPin, sizePin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinList list = getPinValue(runnable, listPin);
        sizePin.getValue(PinInteger.class).setValue(list.size());
    }

    @NonNull
    @Override
    public List<Pin> getDynamicTypePins() {
        return Collections.singletonList(listPin);
    }

}
