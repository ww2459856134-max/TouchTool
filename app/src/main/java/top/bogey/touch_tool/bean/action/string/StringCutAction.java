package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 按前后标记截取文本：如“我是点击器啊”填前部“我是”后部“啊”提取出“点击”
// 后部标记留空时截取到文本末尾，如“1234”只填前部“12”提取出“34”
// 可通过开关把前标记/后标记保留在提取结果中
public class StringCutAction extends CalculateAction {
    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin startPin = new Pin(new PinString(), R.string.string_cut_action_start);
    private final transient Pin endPin = new Pin(new PinString(), R.string.string_cut_action_end);
    private final transient Pin keepStartPin = new Pin(new PinBoolean(), R.string.string_cut_action_keep_start);
    private final transient Pin keepEndPin = new Pin(new PinBoolean(), R.string.string_cut_action_keep_end);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.pin_string, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public StringCutAction() {
        super(ActionType.STRING_CUT);
        addPins(textPin, startPin, endPin, keepStartPin, keepEndPin, resultPin, successPin);
    }

    public StringCutAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(textPin, startPin, endPin, keepStartPin, keepEndPin, resultPin, successPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        String text = getPinValue(runnable, textPin).toString();
        String start = getPinValue(runnable, startPin).toString();
        String end = getPinValue(runnable, endPin).toString();
        boolean keepStart = getPinValue(runnable, keepStartPin) instanceof PinBoolean pinBoolean && pinBoolean.getValue();
        boolean keepEnd = getPinValue(runnable, keepEndPin) instanceof PinBoolean pinBoolean && pinBoolean.getValue();

        resultPin.setValue(new PinString(""));
        successPin.getValue(PinBoolean.class).setValue(false);

        int resultStart = 0;
        int searchFrom = 0;
        // 前部标记非空时定位，找不到则失败
        if (!start.isEmpty()) {
            int position = text.indexOf(start);
            if (position < 0) return;
            searchFrom = position + start.length();
            resultStart = keepStart ? position : searchFrom;
        }

        int resultEnd;
        // 后部标记为空时截取到末尾
        if (end.isEmpty()) {
            resultEnd = text.length();
        } else {
            int position = text.indexOf(end, searchFrom);
            if (position < 0) return;
            resultEnd = keepEnd ? position + end.length() : position;
        }

        if (resultStart > resultEnd) return;

        resultPin.setValue(new PinString(text.substring(resultStart, resultEnd)));
        successPin.getValue(PinBoolean.class).setValue(true);
    }
}
