package top.bogey.touch_tool.bean.action.node;

import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

// 控件展开收起：对支持展开操作的控件执行展开或收起
public class NodeExpandAction extends ExecuteAction {
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node);
    private final transient Pin typePin = new NotLinkAblePin(new PinSingleSelect(R.array.node_expand_type), R.string.node_expand_action_type);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public NodeExpandAction() {
        super(ActionType.NODE_EXPAND);
        addPins(nodePin, typePin, resultPin);
    }

    public NodeExpandAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, typePin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinNode node = getPinValue(runnable, nodePin);
        PinSingleSelect type = getPinValue(runnable, typePin);
        resultPin.getValue(PinBoolean.class).setValue(false);

        if (node != null && node.getNodeInfo() != null && node.getNodeInfo().node != null) {
            int action = type.getIndex() == 0
                    ? AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.getId()
                    : AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.getId();
            resultPin.getValue(PinBoolean.class).setValue(node.getNodeInfo().node.performAction(action));
        }
        executeNext(runnable, outPin);
    }
}
