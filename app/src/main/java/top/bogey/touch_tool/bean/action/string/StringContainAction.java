package top.bogey.touch_tool.bean.action.string;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 文本包含/查找
public class StringContainAction extends CalculateAction {
    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string);
    private final transient Pin findPin = new Pin(new PinString(), R.string.string_contain_action_find);
    private final transient Pin containPin = new Pin(new PinBoolean(), R.string.string_contain_action_contain, true);
    private final transient Pin positionPin = new Pin(new PinInteger(0), R.string.string_contain_action_position, true);
    private final transient Pin countPin = new Pin(new PinInteger(0), R.string.string_contain_action_count, true);

    public StringContainAction() {
        super(ActionType.STRING_CONTAIN);
        addPins(textPin, findPin, containPin, positionPin, countPin);
    }

    public StringContainAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(textPin, findPin, containPin, positionPin, countPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        String text = getPinValue(runnable, textPin).toString();
        String find = getPinValue(runnable, findPin).toString();

        containPin.getValue(PinBoolean.class).setValue(false);
        positionPin.getValue(PinInteger.class).setValue(0);
        countPin.getValue(PinInteger.class).setValue(0);

        if (text.isEmpty() || find.isEmpty()) return;

        int count = 0;
        int first = -1;
        int index = text.indexOf(find);
        while (index >= 0) {
            count++;
            if (first < 0) first = index;
            index = text.indexOf(find, index + find.length());
        }

        if (count > 0) {
            containPin.getValue(PinBoolean.class).setValue(true);
            positionPin.getValue(PinInteger.class).setValue(first + 1);
            countPin.getValue(PinInteger.class).setValue(count);
        }
    }
}
