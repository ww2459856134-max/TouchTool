package top.bogey.touch_tool.bean.action.node;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.service.TaskRunnable;

public class GetNodeParentAction extends CalculateAction {
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node);
    private final transient Pin parentNode = new Pin(new PinNode(), R.string.pin_node, true);

    public GetNodeParentAction() {
        super(ActionType.GET_NODE_PARENT);
        addPins(nodePin, parentNode);
    }

    public GetNodeParentAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, parentNode);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinNode node = getPinValue(runnable, nodePin);
        if (node == null) return;
        NodeInfo nodeInfo = node.getNodeInfo();
        parentNode.getValue(PinNode.class).setNodeInfo(nodeInfo.getParent());
    }
}
