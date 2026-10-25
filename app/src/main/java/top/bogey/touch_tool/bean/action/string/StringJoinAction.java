package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 列表拼接为文本
public class StringJoinAction extends CalculateAction {
    private final transient Pin listPin = new Pin(new PinList(new PinString()), R.string.pin_list);
    private final transient Pin separatorPin = new Pin(new PinString(), R.string.string_join_action_separator);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.pin_string, true);

    public StringJoinAction() {
        super(ActionType.STRING_JOIN);
        addPins(listPin, separatorPin, resultPin);
    }

    public StringJoinAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(listPin, separatorPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinList list = getPinValue(runnable, listPin);
        String separator = getPinValue(runnable, separatorPin).toString();

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            PinObject item = list.get(i);
            if (item == null) continue;
            if (builder.length() > 0) builder.append(separator);
            builder.append(item.toString());
        }
        resultPin.setValue(new PinString(builder.toString()));
    }
}
