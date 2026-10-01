package top.bogey.touch_tool.bean.action.api;

import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// JSON 取值：按路径从 JSON 文本取值，如 data.user.name 或 list[0].price
public class JsonGetValueAction extends CalculateAction {
    private final transient Pin jsonPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin pathPin = new Pin(new PinString(), R.string.json_get_value_action_path);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.pin_string, true);

    public JsonGetValueAction() {
        super(ActionType.JSON_GET_VALUE);
        addPins(jsonPin, pathPin, successPin, resultPin);
    }

    public JsonGetValueAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(jsonPin, pathPin, successPin, resultPin);
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
            successPin.getValue(PinBoolean.class).setValue(true);
        } catch (Exception ignored) {
            // JSON 解析失败保持未取到状态
        }
    }
}
