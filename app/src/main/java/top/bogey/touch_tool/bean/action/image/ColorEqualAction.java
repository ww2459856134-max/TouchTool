package top.bogey.touch_tool.bean.action.image;

import android.graphics.Color;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinColor;
import top.bogey.touch_tool.service.TaskRunnable;

public class ColorEqualAction extends CalculateAction {
    private final transient Pin firstPin = new Pin(new PinColor(), R.string.find_colors_action_template);
    private final transient Pin secondPin = new Pin(new PinColor(), R.string.find_colors_action_template);
    private final transient Pin offsetPin = new Pin(new PinInteger(5), R.string.color_equal_action_offset);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);

    public ColorEqualAction() {
        super(ActionType.COLOR_EQUAL);
        addPins(firstPin, secondPin, offsetPin, resultPin);
    }

    public ColorEqualAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(firstPin, secondPin, offsetPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinColor first = getPinValue(runnable, firstPin);
        PinColor second = getPinValue(runnable, secondPin);
        PinNumber<?> offset = getPinValue(runnable, offsetPin);

        int firstColor = first.getValue().getColor();
        int secondColor = second.getValue().getColor();
        int offsetValue = offset.intValue();

        boolean result = Math.abs(Color.red(firstColor) - Color.red(secondColor)) < offsetValue &&
                Math.abs(Color.green(firstColor) - Color.green(secondColor)) < offsetValue &&
                Math.abs(Color.blue(firstColor) - Color.blue(secondColor)) < offsetValue;

        resultPin.getValue(PinBoolean.class).setValue(result);
    }
}
