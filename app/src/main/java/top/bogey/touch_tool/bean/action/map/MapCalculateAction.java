package top.bogey.touch_tool.bean.action.map;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.action.parent.DynamicTypePinsAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.task.Task;

public abstract class MapCalculateAction extends CalculateAction implements DynamicTypePinsAction {

    public MapCalculateAction(ActionType type) {
        super(type);
    }

    public MapCalculateAction(JsonObject jsonObject) {
        super(jsonObject);
    }

    @Override
    public void onLinkedTo(Task task, Pin origin, Pin to) {
        handleLinkTo(task, origin, to);
        super.onLinkedTo(task, origin, to);
    }

    protected void handleLinkTo(Task task, Pin origin, Pin to) {
        MapActionLinkEventHandler.onLinkedTo(getDynamicTypePins(), getDynamicKeyTypePins(), getDynamicValueTypePins(), task, origin, to);
    }

    @Override
    public void onUnLinkedFrom(Task task, Pin origin, Pin from) {
        handleUnLinkFrom(task, origin);
        super.onUnLinkedFrom(task, origin, from);
    }

    protected void handleUnLinkFrom(Task task, Pin origin) {
        MapActionLinkEventHandler.onUnLinkedFrom(getDynamicTypePins(), getDynamicKeyTypePins(), getDynamicValueTypePins(), task, origin);
    }

    @NonNull
    @Override
    public List<Pin> getDynamicKeyTypePins() {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<Pin> getDynamicValueTypePins() {
        return Collections.emptyList();
    }
}
