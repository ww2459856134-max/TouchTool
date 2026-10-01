package top.bogey.touch_tool.bean.action.map;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinMap;
import top.bogey.touch_tool.service.TaskRunnable;

public class MapClearAction extends MapExecuteAction {
    private final transient Pin mapPin = new Pin(new PinMap());

    public MapClearAction() {
        super(ActionType.MAP_CLEAR);
        addPin(mapPin);
    }

    public MapClearAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(mapPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinMap map = getPinValue(runnable, mapPin);
        map.clear();
        executeNext(runnable, outPin);
    }

    @NonNull
    @Override
    public List<Pin> getDynamicTypePins() {
        return Collections.singletonList(mapPin);
    }

}
