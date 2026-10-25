package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

public class StringFromObjectAction extends CalculateAction {
    private final transient Pin objectPin = new Pin(new PinObject(), R.string.pin_object);
    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string, true);

    public StringFromObjectAction() {
        super(ActionType.STRING_FROM_OBJECT);
        addPins(objectPin, textPin);
    }

    public StringFromObjectAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(objectPin, textPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinObject object = getPinValue(runnable, objectPin);
        String text = null;
        if (object != null) text = object.toString();
        textPin.getValue(PinString.class).setValue(text);
    }
}
