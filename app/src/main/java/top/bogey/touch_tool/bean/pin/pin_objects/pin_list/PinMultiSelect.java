package top.bogey.touch_tool.bean.pin.pin_objects.pin_list;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;

public class PinMultiSelect extends PinList {
    private transient final List<MultiSelectObject> selectObjects = new ArrayList<>();

    public PinMultiSelect(PinObject pinObject) {
        super(PinSubType.MULTI_SELECT, pinObject);
    }

    public PinMultiSelect(JsonObject jsonObject) {
        super(jsonObject);
    }

    public void addSelectObject(MultiSelectObject object) {
        selectObjects.add(object);
    }

    public List<MultiSelectObject> getSelectObjects() {
        return selectObjects;
    }

    public void resetSelectObjects() {
        selectObjects.clear();
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (PinObject object : this) {
            Object value = null;
            if (object instanceof PinString pinString) {
                value = pinString.getValue();
            } else if (object instanceof PinNumber<?> pinNumber) {
                value = pinNumber.getValue();
            }
            for (PinMultiSelect.MultiSelectObject selectObject : selectObjects) {
                if (selectObject.value().equals(value)) {
                    builder.append(selectObject.title()).append("\n");
                }
            }
        }
        return builder.toString().trim();
    }

    public record MultiSelectObject(String title, String description, Object value) {
    }
}
