package top.bogey.touch_tool.bean.task;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;

import top.bogey.touch_tool.bean.base.Identity;
import top.bogey.touch_tool.bean.pin.PinInfo;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinMap;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.PinType;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinApplications;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.save.variable.VariableSaver;
import top.bogey.touch_tool.utils.GsonUtil;

public class Variable extends Identity implements ITagManager {
    private transient Task parent;
    private PinObject value;
    private PinObject saveValue;
    private final TagManager tagManager;

    public Variable(PinObject value) {
        super();
        this.value = value;
        saveValue = (PinObject) value.copy();
        tagManager = new TagManager();
    }

    public Variable(JsonObject jsonObject) {
        super(jsonObject);
        value = (PinObject) GsonUtil.getAsObject(jsonObject, "value", PinBase.class, new PinObject());
        saveValue = (PinObject) GsonUtil.getAsObject(jsonObject, "saveValue", PinBase.class, new PinObject());
        tagManager = GsonUtil.getAsObject(jsonObject, "tagManager", TagManager.class, new TagManager());
    }

    @Override
    public Variable copy() {
        Variable copy = GsonUtil.copy(this, Variable.class);
        copy.parent = parent;
        return copy;
    }

    @Override
    public Variable newCopy() {
        Variable copy = copy();
        copy.setId(UUID.randomUUID().toString());
        copy.parent = null;
        return copy;
    }

    public PinObject getValue() {
        return value;
    }

    public void setValue(PinObject value) {
        this.value = value;
        if (value.getClass().equals(saveValue.getClass())) return;
        saveValue = (PinObject) value.copy();
    }

    public PinObject getSaveValue() {
        return saveValue;
    }

    public void setSaveValue(PinObject saveValue) {
        this.saveValue = saveValue;
    }

    public VariableType getType() {
        if (value instanceof PinList) return VariableType.LIST;
        if (value instanceof PinMap) return VariableType.MAP;
        return VariableType.NORMAL;
    }

    public boolean setType(VariableType type) {
        if (type == getType()) return false;
        PinInfo pinInfo = getPinInfo();
        if (pinInfo.getType() == PinType.APPS) return false;
        switch (type) {
            case NORMAL -> setValue((PinObject) pinInfo.newInstance());
            case LIST -> {
                PinList pinList = new PinList();
                pinList.setValueType((PinObject) pinInfo.newInstance());
                setValue(pinList);
            }
            case MAP -> {
                PinMap pinMap = new PinMap();
                pinMap.setKeyType((PinObject) pinInfo.newInstance());
                pinMap.setValueType((PinObject) pinInfo.newInstance());
                setValue(pinMap);
            }
        }
        return true;
    }

    public PinInfo getPinInfo() {
        return getKeyPinInfo();
    }

    public PinInfo getKeyPinInfo() {
        if (value == null) return null;

        if (value instanceof PinApplications) {
            return PinInfo.getPinInfo(PinType.APPS);
        }

        VariableType type = getType();
        switch (type) {
            case NORMAL -> {
                return PinInfo.getPinInfo(value.getType(), value.getSubType());
            }
            case LIST -> {
                PinList pinList = (PinList) value;
                return PinInfo.getPinInfo(pinList.getValueType());
            }
            case MAP -> {
                PinMap pinMap = (PinMap) value;
                return PinInfo.getPinInfo(pinMap.getKeyType());
            }
        }
        return null;
    }

    public PinInfo getValuePinInfo() {
        if (value == null) return null;
        VariableType type = getType();
        if (type == VariableType.MAP) {
            PinMap pinMap = (PinMap) value;
            return PinInfo.getPinInfo(pinMap.getValueType());
        }
        return null;
    }

    public void setPinInfo(PinInfo pinInfo) {
        setKeyPinInfo(pinInfo);
    }

    public void setKeyPinInfo(PinInfo pinInfo) {
        if (pinInfo.getType() == PinType.APPS) {
            setValue((PinObject) pinInfo.newInstance());
        } else {
            switch (getType()) {
                case NORMAL -> setValue((PinObject) pinInfo.newInstance());
                case LIST -> {
                    PinList pinList;
                    if (value instanceof PinApplications) {
                        pinList = new PinList();
                        setValue(pinList);
                    } else {
                        pinList = (PinList) value;
                    }
                    pinList.setValueType((PinObject) pinInfo.newInstance());
                    pinList.reset();
                }
                case MAP -> {
                    PinMap pinMap = (PinMap) value;
                    pinMap.setKeyType((PinObject) pinInfo.newInstance());
                    pinMap.reset();
                }
            }
        }
    }

    public void setValuePinInfo(PinInfo pinInfo) {
        if (getType() == VariableType.MAP) {
            PinMap pinMap = (PinMap) value;
            pinMap.setValueType((PinObject) pinInfo.newInstance());
        }
    }

    public void save() {
        if (parent != null) parent.save();
        else VariableSaver.getInstance().saveVar(this);
    }

    public Task getParent() {
        return parent;
    }

    public void setParent(Task parent) {
        this.parent = parent;
    }

    @Override
    public void addTag(String tag) {
        tagManager.addTag(tag);
    }

    @Override
    public void removeTag(String tag) {
        tagManager.removeTag(tag);
    }

    @Override
    public List<String> getTags() {
        return tagManager.getTags();
    }

    @Override
    public void setTags(List<String> tags) {
        tagManager.setTags(tags);
    }

    @Override
    public String getTagString() {
        return tagManager.getTagString();
    }

    public static class VariableDeserialize implements JsonDeserializer<Variable> {
        @Override
        public Variable deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return new Variable(json.getAsJsonObject());
        }
    }

    public enum VariableType {
        NORMAL, LIST, MAP
    }
}
