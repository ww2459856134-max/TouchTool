package top.bogey.touch_tool.bean.action.list;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.service.TaskRunnable;

public class ListContainAction extends ListCalculateAction {
    private final transient Pin listPin = new Pin(new PinList());
    private final transient Pin elementPin = new Pin(new PinObject(PinSubType.DYNAMIC), R.string.pin_object);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);

    public ListContainAction() {
        super(ActionType.LIST_CONTAIN);
        addPins(listPin, elementPin, resultPin);
    }

    public ListContainAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(listPin);
        reAddPin(elementPin, true);
        reAddPin(resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinList list = getPinValue(runnable, listPin);
        PinObject element = getPinValue(runnable, elementPin);
        resultPin.getValue(PinBoolean.class).setValue(list.contains(element));
    }

    @NonNull
    @Override
    public List<Pin> getDynamicTypePins() {
        return Arrays.asList(listPin, elementPin);
    }

}
