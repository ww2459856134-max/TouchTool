package top.bogey.touch_tool.bean.action.node;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.function.IntPredicate;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinNodePathString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinNodePathTextString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.ui.custom.float_view.MarkTargetFloatView;

public class IsNodeExistAction extends CalculateAction {
    private final transient Pin typePin = new NotLinkAblePin(new PinSingleSelect(R.array.find_node_type), R.string.is_node_exist_action_type);
    private final transient Pin pathPin = new TypeShowablePin(new PinNodePathString(), R.string.is_node_exist_action_path, t -> t == 0);
    private final transient Pin fullPathPin = new TypeShowablePin(new PinBoolean(true), R.string.is_node_exist_action_full_path, t -> t == 0);
    private final transient Pin textPin = new TypeShowablePin(new PinString(), R.string.pin_string, t -> t == 1);
    private final transient Pin idPin = new TypeShowablePin(new PinString(), R.string.is_node_exist_action_id, t -> t == 2);
    private final transient Pin classPin = new TypeShowablePin(new PinString(), R.string.is_node_exist_action_class, t -> t == 4);
    private final transient Pin descPin = new TypeShowablePin(new PinString(), R.string.is_node_exist_action_node_desc, t -> t == 5);
    private final transient Pin areaPin = new TypeShowablePin(new PinArea(), R.string.pin_area, t -> t != 0 && t != 3);
    private final transient Pin pathTextPin = new TypeShowablePin(new PinNodePathTextString(), R.string.find_node_action_regex_path, t -> t == 3);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);

    public IsNodeExistAction() {
        super(ActionType.IS_NODE_EXIST);
        addPins(typePin, pathPin, fullPathPin, textPin, idPin, classPin, descPin, areaPin, pathTextPin, resultPin);
    }

    public IsNodeExistAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(typePin, pathPin, fullPathPin, textPin, idPin, classPin, descPin, areaPin, pathTextPin, resultPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        PinSingleSelect type = typePin.getValue();
        boolean result = false;

        switch (type.getIndex()) {
            case 0 -> {
                PinObject pathString = getPinValue(runnable, pathPin);
                PinNodePathString path = new PinNodePathString(pathString.toString());
                PinBoolean fullPath = getPinValue(runnable, fullPathPin);
                NodeInfo nodeInfo = path.findNode(NodeInfo.getWindows(), fullPath.getValue());
                if (nodeInfo == null) break;
                result = true;
                MarkTargetFloatView.showTargetArea(nodeInfo.area);
            }
            case 1, 2 -> {
                PinArea area = getPinValue(runnable, areaPin);
                NodeInfo nodeInfo = NodeInfo.getActiveWindow();
                if (nodeInfo == null) break;
                List<NodeInfo> children;
                if (type.getIndex() == 1) {
                    PinObject text = getPinValue(runnable, textPin);
                    String value = text.toString();
                    if (value.isEmpty()) break;
                    children = nodeInfo.findChildrenByText(value, area.getValue());
                } else {
                    PinObject id = getPinValue(runnable, idPin);
                    String value = id.toString();
                    if (value.isEmpty()) break;
                    children = nodeInfo.findChildrenById(value, area.getValue());
                }
                if (children == null || children.isEmpty()) break;
                for (NodeInfo child : children) {
                    MarkTargetFloatView.showTargetArea(child.area);
                }
                result = true;
            }
            case 3 -> {
                PinObject pathString = getPinValue(runnable, pathTextPin);
                PinNodePathTextString path = new PinNodePathTextString(pathString.toString());
                List<NodeInfo> findNodes = path.findNodes(NodeInfo.getWindows());
                if (findNodes == null || findNodes.isEmpty()) break;
                for (NodeInfo findNode : findNodes) {
                    MarkTargetFloatView.showTargetArea(findNode.area);
                }
                result = true;
            }
            case 4 -> {
                PinArea area = getPinValue(runnable, areaPin);
                PinObject className = getPinValue(runnable, classPin);
                NodeInfo nodeInfo = NodeInfo.getActiveWindow();
                if (nodeInfo == null) break;
                List<NodeInfo> children = nodeInfo.findChildrenByClass(className.toString(), area.getValue());
                if (children == null || children.isEmpty()) break;
                for (NodeInfo child : children) {
                    MarkTargetFloatView.showTargetArea(child.area);
                }
                result = true;
            }
            case 5 -> {
                PinArea area = getPinValue(runnable, areaPin);
                PinObject desc = getPinValue(runnable, descPin);
                NodeInfo nodeInfo = NodeInfo.getActiveWindow();
                if (nodeInfo == null) break;
                List<NodeInfo> children = nodeInfo.findChildrenByDesc(desc.toString(), area.getValue());
                if (children == null || children.isEmpty()) break;
                for (NodeInfo child : children) {
                    MarkTargetFloatView.showTargetArea(child.area);
                }
                result = true;
            }
        }

        resultPin.getValue(PinBoolean.class).setValue(result);
    }

    private int getTypeValue() {
        PinSingleSelect type = typePin.getValue();
        return type.getIndex();
    }

    // ---------------- ShowAblePin 子类（按类型显隐，统一实现） ----------------
    private static class TypeShowablePin extends ShowAblePin {
        private final IntPredicate match;

        public TypeShowablePin(PinBase value, int titleId, IntPredicate match) {
            super(value, titleId);
            this.match = match;
        }

        @Override
        public boolean showAble(Task context) {
            IsNodeExistAction action = (IsNodeExistAction) context.getAction(getOwnerId());
            return match.test(action.getTypeValue());
        }
    }
}
