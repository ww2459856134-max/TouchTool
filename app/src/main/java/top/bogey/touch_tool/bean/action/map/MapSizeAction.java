package top.bogey.touch_tool.bean.action.map;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinMap;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.service.TaskRunnable;

public class MapSizeAction extends MapCalculateAction {
    private final transient Pin mapPin = new Pin(new PinMap());
    private final transient Pin sizePin = new Pin(new PinInteger(), R.string.pin_number_integer, true);

    public MapSizeAction() {
        super(ActionType.MAP_SIZE);
        addPins(mapPin, sizePin);
    }

    public MapSizeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(mapPin, sizePin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinMap map = getPinValue(runnable, mapPin);
        sizePin.getValue(PinInteger.class).setValue(map.size());
    }

    @NonNull
    @Override
    public List<Pin> getDynamicTypePins() {
        return Collections.singletonList(mapPin);
    }
}
