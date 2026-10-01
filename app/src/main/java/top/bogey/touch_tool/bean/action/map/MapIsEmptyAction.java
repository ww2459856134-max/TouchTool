package top.bogey.touch_tool.bean.action.map;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinMap;
import top.bogey.touch_tool.service.TaskRunnable;

public class MapIsEmptyAction extends MapCalculateAction {
    private final transient Pin mapPin = new Pin(new PinMap());
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);

    public MapIsEmptyAction() {
        super(ActionType.MAP_IS_EMPTY);
        addPins(mapPin, resultPin);
    }

    public MapIsEmptyAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(mapPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinMap map = getPinValue(runnable, mapPin);
        resultPin.getValue(PinBoolean.class).setValue(map.isEmpty());
    }

    @NonNull
    @Override
    public List<Pin> getDynamicTypePins() {
        return Collections.singletonList(mapPin);
    }

}
