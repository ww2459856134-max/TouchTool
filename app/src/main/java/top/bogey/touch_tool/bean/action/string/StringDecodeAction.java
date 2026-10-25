package top.bogey.touch_tool.bean.action.string;

import android.util.Base64;

import com.google.gson.JsonObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public class StringDecodeAction extends CalculateAction {
    private final transient Pin typePin = new NotLinkAblePin(new PinSingleSelect(R.array.string_encode_type), R.string.string_decode_action_type);
    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.pin_boolean_result, true);
    // 成功出针：解码失败（包括原样返回的情况）时为 false，避免"失败了却像成功"
    // 新针脚追加在末尾，旧任务按位置同步不受影响
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public StringDecodeAction() {
        super(ActionType.STRING_DECODE);
        addPins(typePin, textPin, resultPin, successPin);
    }

    public StringDecodeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(typePin, textPin, resultPin, successPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinSingleSelect type = typePin.getValue();
        PinString text = getPinValue(runnable, textPin);
        String value = text.getValue();
        successPin.getValue(PinBoolean.class).setValue(false);
        if (value == null || value.isEmpty()) return;

        String result;
        boolean success = true;
        result = switch (type.getIndex()) {
            case 0 -> {
                try {
                    yield URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    e.printStackTrace();
                    success = false;
                    yield value;
                }
            }
            case 1 -> {
                try {
                    yield new String(Base64.decode(value, Base64.NO_WRAP));
                } catch (Exception e) {
                    // 非法 Base64 输入不再抛异常打断任务，原样返回但报告失败
                    success = false;
                    yield value;
                }
            }
            default -> value;
        };
        resultPin.getValue(PinString.class).setValue(result);
        successPin.getValue(PinBoolean.class).setValue(success);
    }
}
