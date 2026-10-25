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

// 控件滚动：对可滚动控件执行原生滚动操作
public class NodeScrollAction extends ExecuteAction {
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node);
    private final transient Pin directionPin = new NotLinkAblePin(new PinSingleSelect(R.array.node_scroll_direction), R.string.node_scroll_action_direction);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public NodeScrollAction() {
        super(ActionType.NODE_SCROLL);
        addPins(nodePin, directionPin, resultPin);
    }

    public NodeScrollAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, directionPin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinNode node = getPinValue(runnable, nodePin);
        PinSingleSelect direction = getPinValue(runnable, directionPin);
        resultPin.getValue(PinBoolean.class).setValue(false);

        if (node != null && node.getNodeInfo() != null && node.getNodeInfo().node != null) {
            int action = direction.getIndex() == 0
                    ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            resultPin.getValue(PinBoolean.class).setValue(node.getNodeInfo().node.performAction(action));
        }
        executeNext(runnable, outPin);
    }
}
