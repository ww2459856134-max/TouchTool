package top.bogey.touch_tool.bean.action.api;

import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.PinMap;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// JSON 取值：按路径从 JSON 文本取值，如 data.user.name 或 list[0].price
// 除文本输出外提供动态输出：数组→列表、对象→映射，可直接连遍历列表/映射
public class JsonGetValueAction extends CalculateAction {
    private final transient Pin jsonPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin pathPin = new Pin(new PinString(), R.string.json_get_value_action_path);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.pin_string, true);
    private final transient Pin dynamicPin = new Pin(new PinObject(PinSubType.DYNAMIC), R.string.pin_dynamic_result, true);

    public JsonGetValueAction() {
        super(ActionType.JSON_GET_VALUE);
        addPins(jsonPin, pathPin, successPin, resultPin, dynamicPin);
    }

    public JsonGetValueAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(jsonPin, pathPin, successPin, resultPin, dynamicPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        String json = getPinValue(runnable, jsonPin).toString();
        String path = getPinValue(runnable, pathPin).toString();

        successPin.getValue(PinBoolean.class).setValue(false);
        resultPin.setValue(new PinString(""));

        if (json.isEmpty() || path.isEmpty()) return;

        try {
            Object current = json.trim().startsWith("[") ? new JSONArray(json) : new JSONObject(json);
            // 按 "." 分段，每段支持 name、name[0]、name[0][1] 与纯 [0] 形式
            for (String segment : path.split("\\.")) {
                if (current == null) return;
                Matcher matcher = Pattern.compile("^([^.\\[]*)(\\[[0-9]+])*$").matcher(segment);
                if (!matcher.matches()) return;
                String name = matcher.group(1);

                if (!name.isEmpty()) {
                    if (current instanceof JSONObject object) {
                        if (!object.has(name)) return;
                        current = object.get(name);
                    } else return;
                }

                Matcher indexMatcher = Pattern.compile("\\[([0-9]+)]").matcher(segment);
                while (indexMatcher.find()) {
                    int index = Integer.parseInt(indexMatcher.group(1));
                    if (current instanceof JSONArray array) {
                        if (index >= array.length()) return;
                        current = array.get(index);
                    } else return;
                }
            }

            String value = current == null ? "" : current instanceof String ? (String) current : current.toString();
            resultPin.setValue(new PinString(value));
            dynamicPin.setValue(toPinObject(current));
            successPin.getValue(PinBoolean.class).setValue(true);
        } catch (Exception ignored) {
            // JSON 解析失败保持未取到状态
        }
    }

    // JSON 值转对应针脚对象：数组→列表、对象→映射（递归嵌套）、其余→基础类型
    private PinObject toPinObject(Object value) {
        if (value == null) return new PinString("");
        if (value instanceof String s) return new PinString(s);
        if (value instanceof Boolean b) return new PinBoolean(b);
        if (value instanceof Number n) return new PinDouble(n.doubleValue());
        if (value instanceof JSONArray array) {
            PinList list = new PinList();
            for (int i = 0; i < array.length(); i++) {
                list.add(toPinObject(array.opt(i)));
            }
            return list;
        }
        if (value instanceof JSONObject object) {
            PinMap map = new PinMap();
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(new PinString(key), toPinObject(object.opt(key)));
            }
            return map;
        }
        return new PinString(value.toString());
    }
}
