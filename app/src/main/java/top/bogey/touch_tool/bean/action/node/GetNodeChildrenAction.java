package top.bogey.touch_tool.bean.action.node;

import com.google.gson.JsonObject;

import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.service.TaskRunnable;

public class GetNodeChildrenAction extends CalculateAction {
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node);
    private final transient Pin childrenPin = new Pin(new PinList(new PinNode()), true);
    private final transient Pin getAllPin = new Pin(new PinBoolean(false), R.string.get_node_children_action_get_all);

    public GetNodeChildrenAction() {
        super(ActionType.GET_NODE_CHILDREN);
        addPins(nodePin, childrenPin, getAllPin);
    }

    public GetNodeChildrenAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, childrenPin, getAllPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinNode node = getPinValue(runnable, nodePin);
        if (node == null) return;
        NodeInfo nodeInfo = node.getNodeInfo();
        if (nodeInfo == null) return;
        PinBoolean getAll = getPinValue(runnable, getAllPin);

        PinList childrenPinValue = childrenPin.getValue(PinList.class);
        if (getAll.getValue()) {
            List<NodeInfo> children = nodeInfo.getChildren();
            while (!children.isEmpty()) {
                NodeInfo info = children.remove(0);
                childrenPinValue.add(new PinNode(info));
                children.addAll(info.getChildren());
            }
        } else {
            for (NodeInfo child : nodeInfo.getChildren()) {
                childrenPinValue.add(new PinNode(child));
            }
        }
    }
}
