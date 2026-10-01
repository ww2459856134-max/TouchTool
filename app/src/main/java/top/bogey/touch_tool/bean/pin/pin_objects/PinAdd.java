package top.bogey.touch_tool.bean.pin.pin_objects;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.utils.GsonUtil;

public class PinAdd extends PinBase {
    private final List<Pin> pins = new ArrayList<>();

    public PinAdd(Pin pin) {
        super(PinType.ADD);
        pins.add(pin.copy());
    }

    public PinAdd(List<Pin> pins) {
        super(PinType.ADD);
        this.pins.addAll(pins);
    }

    public PinAdd(JsonObject jsonObject) {
        super(jsonObject);
        pins.addAll(GsonUtil.getAsObject(jsonObject, "pins", TypeToken.getParameterized(ArrayList.class, Pin.class).getType(), new ArrayList<>()));
    }

    @Override
    public void reset() {

    }

    @Override
    public void sync(PinBase value) {
        if (value instanceof PinAdd pinAdd) {
            if (pins.size() != pinAdd.pins.size()) return;
            for (int i = 0; i < pins.size(); i++) {
                Pin pin = pins.get(i);
                pin.sync(pinAdd.pins.get(i));
            }
        }
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public boolean linkFromAble(PinBase pin) {
        return false;
    }

    @Override
    public boolean linkToAble(PinBase pin) {
        return false;
    }

    public List<Pin> getPins() {
        return pins;
    }

    public Pin getPin() {
        return getPin(0);
    }

    public Pin getPin(int index) {
        if (index < 0 || index >= pins.size()) return null;
        return pins.get(index);
    }

    public void setPins(List<Pin> pins) {
        this.pins.clear();
        this.pins.addAll(pins);
    }

    public void setPin(Pin pin) {
        this.pins.clear();
        this.pins.add(pin);
    }
}
