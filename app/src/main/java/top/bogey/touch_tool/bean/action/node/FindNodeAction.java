package top.bogey.touch_tool.bean.action.node;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
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
import top.bogey.touch_tool.utils.AppUtil;

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
    private final transient Pin matchModePin = new FilterShowablePin(new PinSingleSelect(R.array.node_match_mode), R.string.find_node_action_match_mode);
    private final transient Pin visiblePin = new FilterShowablePin(new PinBoolean(true), R.string.find_node_action_only_visible);
    private final transient Pin clickablePin = new FilterShowablePin(new PinBoolean(), R.string.find_node_action_only_clickable);
    private final transient Pin indexPin = new NotLinkAblePin(new PinInteger(0), R.string.find_node_action_index);
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node, true);
    private final transient Pin nodesPin = new NotPathShowablePin(new PinList(new PinNode()), true);

    public FindNodeAction() {
        super(ActionType.FIND_NODE);
        addPins(typePin, pathPin, fullPathPin, textPin, idPin, classPin, descPin, areaPin, pathTextPin, matchModePin, visiblePin, clickablePin, indexPin, nodePin, nodesPin);
    }

    public FindNodeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(typePin, pathPin, fullPathPin, textPin, idPin, classPin, descPin, areaPin, pathTextPin, matchModePin, visiblePin, clickablePin, indexPin, nodePin, nodesPin);
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
                if (nodeInfo == null) return false;
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
                    if (value.isEmpty()) return false;
                    children = nodeInfo.findChildrenByText(value, area.getValue());
                } else {
                    PinObject id = getPinValue(runnable, idPin);
                    String value = id.toString();
                    if (value.isEmpty()) return false;
                    children = nodeInfo.findChildrenById(value, area.getValue());
                }

                children = applyFilters(runnable, children);
                if (children == null || children.isEmpty()) return false;
                fillNodes(runnable, nodes, children);
                return true;
            }
            case 3 -> {
                PinObject pathString = getPinValue(runnable, pathTextPin);
                PinNodePathTextString path = new PinNodePathTextString(pathString.toString());
                List<NodeInfo> findNodes = path.findNodes(NodeInfo.getWindows());
                findNodes = applyFilters(runnable, findNodes);
                if (findNodes == null || findNodes.isEmpty()) return false;
                fillNodes(runnable, nodes, findNodes);
                return true;
            }
            case 4, 5 -> {
                PinArea area = getPinValue(runnable, areaPin);
                NodeInfo nodeInfo = NodeInfo.getActiveWindow();
                if (nodeInfo == null) return false;

                List<NodeInfo> children;
                if (type.getIndex() == 4) {
                    PinObject className = getPinValue(runnable, classPin);
                    children = nodeInfo.findChildrenByClass(className.toString(), area.getValue());
                } else {
                    PinObject desc = getPinValue(runnable, descPin);
                    children = nodeInfo.findChildrenByDesc(desc.toString(), area.getValue());
                }

                children = applyFilters(runnable, children);
                if (children == null || children.isEmpty()) return false;
                fillNodes(runnable, nodes, children);
                return true;
            }
            case 6 -> {
                return findCombined(runnable, nodes);
            }
        }
        return false;
    }

    // 组合查找：文本、ID、类名、描述条件同时满足（未填的条件不参与匹配）
    private boolean findCombined(TaskRunnable runnable, PinList nodes) {
        NodeInfo root = NodeInfo.getActiveWindow();
        if (root == null) return false;

        String text = getPinValue(runnable, textPin).toString();
        String id = getPinValue(runnable, idPin).toString();
        String clazz = getPinValue(runnable, classPin).toString();
        String desc = getPinValue(runnable, descPin).toString();
        // 至少要填一个条件
        if (text.isEmpty() && id.isEmpty() && clazz.isEmpty() && desc.isEmpty()) return false;

        int mode = getMatchMode();
        boolean onlyVisible = getBooleanValue(runnable, visiblePin);
        boolean onlyClickable = getBooleanValue(runnable, clickablePin);
        PinArea area = getPinValue(runnable, areaPin);

        List<NodeInfo> result = new ArrayList<>();
        collectNodes(root, mode, text, id, clazz, desc, onlyVisible, onlyClickable, area.getValue(), result);

        if (result.isEmpty()) return false;
        fillNodes(runnable, nodes, result);
        return true;
    }

    // 深度优先收集满足全部条件的控件
    private void collectNodes(NodeInfo parent, int mode, String text, String id, String clazz, String desc,
                              boolean onlyVisible, boolean onlyClickable, Rect area, List<NodeInfo> result) {
        if (parent == null) return;

        boolean areaOk = area == null || area.isEmpty() || area.contains(parent.area) || Rect.intersects(area, parent.area);
        if (areaOk && (!onlyVisible || parent.visible) && (!onlyClickable || (parent.node != null && parent.node.isClickable()))) {
            boolean ok = true;
            if (!text.isEmpty()) ok = matchByMode(parent.text, text, mode);
            if (ok && !id.isEmpty()) ok = matchByMode(parent.id, id, mode);
            if (ok && !clazz.isEmpty()) ok = matchByMode(parent.clazz, clazz, mode);
            if (ok && !desc.isEmpty()) ok = matchByMode(parent.desc, desc, mode);
            if (ok) result.add(parent);
        }

        for (NodeInfo child : parent.getChildren()) {
            collectNodes(child, mode, text, id, clazz, desc, onlyVisible, onlyClickable, area, result);
        }
    }

    // 按匹配方式比较两个字符串
    private boolean matchByMode(String value, String condition, int mode) {
        if (value == null) return false;
        switch (mode) {
            case 0 -> {
                return value.equals(condition);
            }
            case 2 -> {
                Pattern pattern = AppUtil.getPattern(condition);
                if (pattern != null) return pattern.matcher(value).find();
                return value.toLowerCase().contains(condition.toLowerCase());
            }
            case 3 -> {
                return value.startsWith(condition);
            }
            case 4 -> {
                return value.endsWith(condition);
            }
            default -> {
                return value.toLowerCase().contains(condition.toLowerCase());
            }
        }
    }

    // 对查找到的节点列表应用可见性、可点击过滤
    private List<NodeInfo> applyFilters(TaskRunnable runnable, List<NodeInfo> children) {
        if (children == null || children.isEmpty()) return children;

        boolean onlyVisible = getBooleanValue(runnable, visiblePin);
        boolean onlyClickable = getBooleanValue(runnable, clickablePin);
        if (!onlyVisible && !onlyClickable) return children;

        List<NodeInfo> filtered = new ArrayList<>();
        for (NodeInfo node : children) {
            if (onlyVisible && !node.visible) continue;
            if (onlyClickable && (node.node == null || !node.node.isClickable())) continue;
            filtered.add(node);
        }
        return filtered;
    }

    private int getMatchMode() {
        PinSingleSelect mode = matchModePin.getValue();
        return mode == null ? 1 : mode.getIndex();
    }

    private boolean getBooleanValue(TaskRunnable runnable, Pin conditionPin) {
        return getPinValue(runnable, conditionPin) instanceof PinBoolean pinBoolean && pinBoolean.getValue();
    }

    // 把结果列表填入输出针脚，并按输出序号选择单个节点
    private void fillNodes(TaskRunnable runnable, PinList nodes, List<NodeInfo> list) {
        for (NodeInfo nodeInfo : list) {
            nodes.add(new PinNode(nodeInfo));
            MarkTargetFloatView.showTargetArea(nodeInfo.area);
        }

        int index = 0;
        if (getPinValue(runnable, indexPin) instanceof PinInteger pinInteger) {
            index = pinInteger.getValue();
        }
        if (index < 0) index = nodes.size() + index;
        if (index < 0 || index >= nodes.size()) index = 0;
        nodePin.setValue(nodes.get(index));
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

    // 匹配方式、可见性过滤、可点击过滤：在文本/ID/类名/描述/组合模式下显示
    private static class FilterShowablePin extends ShowAblePin {
        public FilterShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            int type = action.getTypeValue();
            return type == 1 || type == 2 || type == 4 || type == 5 || type == 6;
        }
    }

    private static class TextShowablePin extends ShowAblePin {
        public TextShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 1 || action.getTypeValue() == 6;
        }
    }

    private static class IdShowablePin extends ShowAblePin {
        public IdShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 2 || action.getTypeValue() == 6;
        }
    }

    private static class ClassShowablePin extends ShowAblePin {
        public ClassShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 4 || action.getTypeValue() == 6;
        }
    }

    private static class DescShowablePin extends ShowAblePin {
        public DescShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 5 || action.getTypeValue() == 6;
        }
    }
}
