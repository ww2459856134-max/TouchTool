package top.bogey.touch_tool.bean.action.string;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.util.Map;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.action.parent.SyncAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinMap;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinFileContentString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;

public class ParseJsonAction extends CalculateAction implements SyncAction {
    private final transient Pin jsonPin = new Pin(new PinFileContentString(), R.string.pin_string);
    private final transient Pin resultPin = new Pin(new PinMap(), R.string.pin_boolean_result, true);

    public ParseJsonAction() {
        super(ActionType.PARSE_JSON);
        addPins(jsonPin, resultPin);
    }

    public ParseJsonAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(jsonPin);
        reAddPin(resultPin, true);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinObject json = getPinValue(runnable, jsonPin);
        PinBase parsed = parseJsonSafe(json.toString());
        // 解析失败时保持出针原值不动，不再抛异常打断任务
        if (parsed != null) resultPin.setValue(parsed);
    }

    // 安全解析 JSON：对象/数组走 Gson，标量（true/false/数字/其他）按类型兜底
    // 全程捕获异常：接口返回半截数据时最多是解析失败，不会打断任务
    private PinBase parseJsonSafe(String jsonString) {
        if (jsonString == null) return null;
        String s = jsonString.trim();
        if (s.isEmpty()) return null;
        Gson gson = new Gson();
        try {
            if (s.startsWith("{")) {
                Map<String, Object> map = gson.fromJson(s, new TypeToken<Map<String, Object>>() {
                }.getType());
                return PinBase.parseValue(map);
            }
            if (s.startsWith("[")) {
                List<Object> list = gson.fromJson(s, new TypeToken<List<Object>>() {
                }.getType());
                return PinBase.parseValue(list);
            }
        } catch (Exception ignored) {
            return null;
        }
        // 标量 JSON：数字、布尔、null、带引号字符串等
        try {
            if (s.equalsIgnoreCase("true")) return new PinBoolean(true);
            if (s.equalsIgnoreCase("false")) return new PinBoolean(false);
            if (s.matches("-?\\d+")) return new PinInteger(Integer.parseInt(s));
            if (s.matches("-?\\d+\\.\\d+([eE][+-]?\\d+)?")) return new PinDouble(Double.parseDouble(s));
        } catch (Exception ignored) {
        }
        return new PinString(s);
    }

    @Override
    public void sync(Task context) {
        resultPin.setValue(new PinMap());
        if (jsonPin.isLinked()) return;
        PinFileContentString pinValue = jsonPin.getValue(PinFileContentString.class);
        PinBase parsed = parseJsonSafe(pinValue.getValue());
        if (parsed != null) {
            resultPin.setValue(context, parsed);
        }
    }

    @Override
    public void onValueUpdated(Task task, Pin origin, PinBase value) {
        super.onValueUpdated(task, origin, value);
        sync(task);
    }
}
