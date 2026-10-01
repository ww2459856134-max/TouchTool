package top.bogey.touch_tool.bean.action.node;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.service.TaskRunnable;

public class CheckNodeValidAction extends CalculateAction {
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);

    public CheckNodeValidAction() {
        super(ActionType.CHECK_NODE_VALID);
        addPins(nodePin, resultPin);
    }

    public CheckNodeValidAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinNode node = getPinValue(runnable, nodePin);
        NodeInfo nodeInfo = node.getNodeInfo();
        resultPin.getValue(PinBoolean.class).setValue(nodeInfo != null);
    }
}
