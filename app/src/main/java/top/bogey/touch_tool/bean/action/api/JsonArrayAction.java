package top.bogey.touch_tool.bean.action.api;

import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

// JSON 数组：取数组长度或按下标取元素
public class JsonArrayAction extends CalculateAction {
    private final transient Pin jsonPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin modePin = new NotLinkAblePin(new PinSingleSelect(R.array.json_array_mode), R.string.json_array_action_mode);
    private final transient Pin indexPin = new NotLinkAblePin(new PinInteger(0), R.string.json_array_action_index);
    private final transient Pin lengthPin = new Pin(new PinInteger(0), R.string.json_array_action_length, true);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.pin_string, true);

    public JsonArrayAction() {
        super(ActionType.JSON_ARRAY);
        addPins(jsonPin, modePin, indexPin, lengthPin, resultPin);
    }

    public JsonArrayAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(jsonPin, modePin, indexPin, lengthPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        String json = getPinValue(runnable, jsonPin).toString();

        lengthPin.getValue(PinInteger.class).setValue(0);
        resultPin.setValue(new PinString(""));

        if (json.isEmpty()) return;

        try {
            JSONArray array;
            Object parsed = json.trim().startsWith("[") ? new JSONArray(json) : new JSONObject(json);
            // 传入对象时若只有一个键且值是数组，自动下钻一层
            if (parsed instanceof JSONObject object) {
                if (object.length() == 1) {
                    String key = object.names() == null ? null : object.names().getString(0);
                    if (key != null && object.get(key) instanceof JSONArray inner) {
                        array = inner;
                    } else return;
                } else return;
            } else {
                array = (JSONArray) parsed;
            }

            lengthPin.getValue(PinInteger.class).setValue(array.length());
            PinSingleSelect mode = modePin.getValue();
            if (mode != null && mode.getIndex() == 1) {
                int index = 0;
                if (getPinValue(runnable, indexPin) instanceof PinInteger pinInteger) index = pinInteger.getValue();
                if (index < 0) index = array.length() + index;
                if (index >= 0 && index < array.length()) {
                    Object element = array.get(index);
                    resultPin.setValue(new PinString(element instanceof String ? (String) element : element.toString()));
                }
            }
        } catch (Exception ignored) {
            // JSON 解析失败保持空输出
        }
    }
}
