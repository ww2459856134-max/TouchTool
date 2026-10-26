package top.bogey.touch_tool.bean.action.node;

import com.google.gson.JsonObject;

import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.FindExecuteAction;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinNodePathString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinNodePathTextString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.ui.custom.float_view.MarkTargetFloatView;

public class FindNodeAction extends FindExecuteAction {
    private final transient Pin typePin = new NotLinkAblePin(new PinSingleSelect(R.array.find_node_type), R.string.find_node_action_type);
    private final transient Pin pathPin = new PathShowablePin(new PinNodePathString(), R.string.find_node_action_path);
    private final transient Pin fullPathPin = new PathShowablePin(new PinBoolean(true), R.string.find_node_action_full_path);
    private final transient Pin textPin = new TextShowablePin(new PinString(), R.string.pin_string);
    private final transient Pin idPin = new IdShowablePin(new PinString(), R.string.find_node_action_id);
    private final transient Pin classPin = new ClassShowablePin(new PinString(), R.string.find_node_action_class);
    private final transient Pin descPin = new DescShowablePin(new PinString(), R.string.find_node_action_node_desc);
    private final transient Pin areaPin = new NotAllPathShowablePin(new PinArea(), R.string.pin_area);
    private final transient Pin pathTextPin = new PathTextShowablePin(new PinNodePathTextString(), R.string.find_node_action_regex_path);
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node, true);
    private final transient Pin nodesPin = new NotPathShowablePin(new PinList(new PinNode()), true);
    // 运行诊断：显示查找失败的具体输入与窗口情况（排查用）
    private final transient Pin diagPin = new NotLinkAblePin(new PinString(), R.string.node_find_diag);

    public FindNodeAction() {
        super(ActionType.FIND_NODE);
        reAddPins(typePin, pathPin, fullPathPin, textPin, idPin, classPin, descPin, areaPin, pathTextPin, nodePin, nodesPin);
        addPin(diagPin);
    }

    public FindNodeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(typePin, pathPin, fullPathPin, textPin, idPin, classPin, descPin, areaPin, pathTextPin, nodePin, nodesPin);
        reAddPins(diagPin);
        // 恢复诊断：针脚队列仍有滞留 = 恢复错位的铁证
        StringBuilder leftover = new StringBuilder();
        String diagTitle = MainApplication.getInstance().getString(R.string.node_find_diag);
        for (Pin tmpPin : tmpPins) {
            if (diagTitle.equals(tmpPin.getTitle())) continue;
            leftover.append(tmpPin.getTitle()).append("(").append(tmpPin.getValue().getClass().getSimpleName()).append(") ");
        }
        if (leftover.length() > 0) {
            diagPin.setValue(new PinString("加载错位! 滞留针脚: " + leftover));
        }
    }

    private void setDiag(String reason) {
        diagPin.setValue(new PinString(reason));
    }

    @Override
    public boolean find(TaskRunnable runnable) {
        PinSingleSelect type = typePin.getValue();
        PinList nodes = nodesPin.getValue();

        switch (type.getIndex()) {
            case 0 -> {
                PinObject pathString = getPinValue(runnable, pathPin);
                PinNodePathString path = new PinNodePathString(pathString.toString());
                PinBoolean fullPath = getPinValue(runnable, fullPathPin);
                NodeInfo nodeInfo = path.findNode(NodeInfo.getWindows(), fullPath.getValue());
                if (nodeInfo == null) {
                    setDiag("身份查找未命中: 值=" + pathString + " 精确=" + fullPath.getValue() + " 窗口数=" + NodeInfo.getWindows().size());
                    return false;
                }
                nodePin.getValue(PinNode.class).setNodeInfo(nodeInfo);
                MarkTargetFloatView.showTargetArea(nodeInfo.area);
                return true;
            }
            case 1, 2 -> {
                PinArea area = getPinValue(runnable, areaPin);
                NodeInfo nodeInfo = NodeInfo.getActiveWindow();
                if (nodeInfo == null) return false;

                List<NodeInfo> children;
                if (type.getIndex() == 1) {
                    PinObject text = getPinValue(runnable, textPin);
                    String value = text.toString();
                    if (value.isEmpty()) {
                        setDiag("文本查找: 输入为空");
                        return false;
                    }
                    children = nodeInfo.findChildrenByText(value, area.getValue());
                } else {
                    PinObject id = getPinValue(runnable, idPin);
                    String value = id.toString();
                    if (value.isEmpty()) {
                        setDiag("ID查找: 输入为空");
                        return false;
                    }
                    children = nodeInfo.findChildrenById(value, area.getValue());
                }

                if (children == null || children.isEmpty()) {
                    setDiag("文本/ID查找未命中: 窗口=" + nodeInfo + " 值见上方针脚");
                    return false;
                }
                // 修复：重试时先清空上次结果，避免列表累积重复
                nodes.clear();
                for (NodeInfo child : children) {
                    nodes.add(new PinNode(child));
                    MarkTargetFloatView.showTargetArea(child.area);
                }
                nodePin.setValue(nodes.get(0));
                return true;
            }
            case 3 -> {
                PinObject pathString = getPinValue(runnable, pathTextPin);
                PinNodePathTextString path = new PinNodePathTextString(pathString.toString());
                List<NodeInfo> findNodes = path.findNodes(NodeInfo.getWindows());
                if (findNodes == null || findNodes.isEmpty()) {
                    setDiag("正则路径未命中: 值=" + pathString);
                    return false;
                }
                // 修复：重试时先清空上次结果，避免列表累积重复
                nodes.clear();
                for (NodeInfo nodeInfo : findNodes) {
                    nodes.add(new PinNode(nodeInfo));
                    MarkTargetFloatView.showTargetArea(nodeInfo.area);
                }
                nodePin.setValue(nodes.get(0));
                return true;
            }
            case 4 -> {
                PinArea area = getPinValue(runnable, areaPin);
                PinObject className = getPinValue(runnable, classPin);
                NodeInfo nodeInfo = NodeInfo.getActiveWindow();
                if (nodeInfo == null) return false;
                List<NodeInfo> children = nodeInfo.findChildrenByClass(className.toString(), area.getValue());
                if (children == null || children.isEmpty()) {
                    setDiag("类名查找未命中: 值=" + className + " 窗口=" + nodeInfo);
                    return false;
                }
                // 修复：重试时先清空上次结果，避免列表累积重复
                nodes.clear();
                for (NodeInfo child : children) {
                    nodes.add(new PinNode(child));
                    MarkTargetFloatView.showTargetArea(child.area);
                }
                nodePin.setValue(nodes.get(0));
                return true;
            }
            case 5 -> {
                PinArea area = getPinValue(runnable, areaPin);
                PinObject desc = getPinValue(runnable, descPin);
                NodeInfo nodeInfo = NodeInfo.getActiveWindow();
                if (nodeInfo == null) return false;
                List<NodeInfo> children = nodeInfo.findChildrenByDesc(desc.toString(), area.getValue());
                if (children == null || children.isEmpty()) {
                    setDiag("描述查找未命中: 值=" + desc + " 窗口=" + nodeInfo);
                    return false;
                }
                // 修复：重试时先清空上次结果，避免列表累积重复
                nodes.clear();
                for (NodeInfo child : children) {
                    nodes.add(new PinNode(child));
                    MarkTargetFloatView.showTargetArea(child.area);
                }
                nodePin.setValue(nodes.get(0));
                return true;
            }
        }
        return false;
    }

    private int getTypeValue() {
        PinSingleSelect type = typePin.getValue();
        return type.getIndex();
    }

    public void setTypeValue(int index) {
        PinSingleSelect type = typePin.getValue();
        type.setIndex(index);
    }

    public Pin getPathPin() {
        return pathPin;
    }

    public Pin getTextPin() {
        return textPin;
    }

    public Pin getNodePin() {
        return nodePin;
    }

    private static class PathShowablePin extends ShowAblePin {
        public PathShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 0;
        }
    }

    private static class PathTextShowablePin extends ShowAblePin {
        public PathTextShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 3;
        }
    }

    private static class NotAllPathShowablePin extends ShowAblePin {
        public NotAllPathShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() != 0 && action.getTypeValue() != 3;
        }
    }

    private static class NotPathShowablePin extends ShowAblePin {
        public NotPathShowablePin(PinBase value, boolean out) {
            super(value, out);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() != 0;
        }
    }


    private static class TextShowablePin extends ShowAblePin {
        public TextShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 1;
        }
    }

    private static class IdShowablePin extends ShowAblePin {
        public IdShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 2;
        }
    }

    private static class ClassShowablePin extends ShowAblePin {
        public ClassShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 4;
        }
    }

    private static class DescShowablePin extends ShowAblePin {
        public DescShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 5;
        }
    }
}
