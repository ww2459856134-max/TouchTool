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
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinConditionsString;
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
    private final transient Pin logicPin = new LogicShowablePin(new PinSingleSelect(R.array.node_logic_mode), R.string.find_node_action_logic);
    private final transient Pin conditionsPin = new FilterShowablePin(new PinConditionsString(), R.string.find_node_action_conditions);
    private final transient Pin visiblePin = new FilterShowablePin(new PinBoolean(true), R.string.find_node_action_only_visible);
    private final transient Pin clickablePin = new FilterShowablePin(new PinBoolean(), R.string.find_node_action_only_clickable);
    private final transient Pin indexPin = new NotLinkAblePin(new PinInteger(0), R.string.find_node_action_index);
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node, true);
    private final transient Pin nodesPin = new NotPathShowablePin(new PinList(new PinNode()), true);
    // 虚拟屏ID：填了只在指定屏幕里找控件（0 = 全部屏幕）

    public FindNodeAction() {
        super(ActionType.FIND_NODE);
        addPins(typePin, pathPin, fullPathPin, textPin, idPin, classPin, descPin, areaPin, pathTextPin, matchModePin, logicPin, conditionsPin, visiblePin, clickablePin, indexPin, nodePin, nodesPin);
    }

    public FindNodeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(typePin, pathPin, fullPathPin, textPin, idPin, classPin, descPin, areaPin, pathTextPin, matchModePin, logicPin, conditionsPin, visiblePin, clickablePin, indexPin, nodePin, nodesPin);
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
                children = applyExtraConditions(runnable, children);
                if (children == null || children.isEmpty()) return false;
                fillNodes(runnable, nodes, children);
                return true;
            }
            case 3 -> {
                PinObject pathString = getPinValue(runnable, pathTextPin);
                PinNodePathTextString path = new PinNodePathTextString(pathString.toString());
                List<NodeInfo> findNodes = path.findNodes(NodeInfo.getWindows());
                findNodes = applyFilters(runnable, findNodes);
                findNodes = applyExtraConditions(runnable, findNodes);
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
                children = applyExtraConditions(runnable, children);
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

    // 组合查找：基础条件 + 附加条件列表按匹配逻辑（AND/OR）组合（未填的条件不参与匹配）
    private boolean findCombined(TaskRunnable runnable, PinList nodes) {
        NodeInfo root = NodeInfo.getActiveWindow();
        if (root == null) return false;

        int mode = getMatchMode();
        List<NodeCondition> conditions = new ArrayList<>();
        addBaseCondition(conditions, NodeCondition.FIELD_TEXT, mode, getPinValue(runnable, textPin).toString());
        addBaseCondition(conditions, NodeCondition.FIELD_ID, mode, getPinValue(runnable, idPin).toString());
        addBaseCondition(conditions, NodeCondition.FIELD_CLASS, mode, getPinValue(runnable, classPin).toString());
        addBaseCondition(conditions, NodeCondition.FIELD_DESC, mode, getPinValue(runnable, descPin).toString());
        conditions.addAll(getExtraConditions(runnable));
        if (conditions.isEmpty()) return false;

        int logic = getLogicMode();
        boolean onlyVisible = getBooleanValue(runnable, visiblePin);
        boolean onlyClickable = getBooleanValue(runnable, clickablePin);
        PinArea area = getPinValue(runnable, areaPin);

        List<NodeInfo> result = new ArrayList<>();
        collectNodes(root, logic, conditions, onlyVisible, onlyClickable, area.getValue(), result);

        if (result.isEmpty()) return false;
        fillNodes(runnable, nodes, result);
        return true;
    }

    // 基础条件转为附加条件同构的形式（未填的不参与匹配）
    private void addBaseCondition(List<NodeCondition> conditions, int field, int mode, String value) {
        if (value == null || value.isEmpty()) return;
        conditions.add(new NodeCondition(field, mode, value));
    }

    // 深度优先收集满足条件的控件，logic: 0=全部满足(AND) 1=任一满足(OR)
    private void collectNodes(NodeInfo parent, int logic, List<NodeCondition> conditions,
                              boolean onlyVisible, boolean onlyClickable, Rect area, List<NodeInfo> result) {
        if (parent == null) return;

        boolean areaOk = area == null || area.isEmpty() || area.contains(parent.area) || Rect.intersects(area, parent.area);
        if (areaOk && (!onlyVisible || parent.visible) && (!onlyClickable || (parent.node != null && parent.node.isClickable()))) {
            boolean ok = logic == 0;
            for (NodeCondition condition : conditions) {
                boolean matched = matchCondition(parent, condition);
                if (logic == 0) {
                    if (!matched) {
                        ok = false;
                        break;
                    }
                } else if (matched) {
                    ok = true;
                    break;
                }
            }
            if (ok) result.add(parent);
        }

        for (NodeInfo child : parent.getChildren()) {
            collectNodes(child, logic, conditions, onlyVisible, onlyClickable, area, result);
        }
    }

    // 附加条件针脚里存的条件列表
    private List<NodeCondition> getExtraConditions(TaskRunnable runnable) {
        if (getPinValue(runnable, conditionsPin) instanceof PinConditionsString pin) {
            return NodeCondition.fromJson(pin.toString());
        }
        return new ArrayList<>();
    }

    // 单条条件求值：文本类字段用条件自带的运算符（与匹配方式一致），数值/状态字段用各自的运算符
    private boolean matchCondition(NodeInfo node, NodeCondition condition) {
        String value = condition.value == null ? "" : condition.value.trim();
        switch (condition.field) {
            case NodeCondition.FIELD_TEXT -> {
                return matchByMode(node.text, value, condition.op);
            }
            case NodeCondition.FIELD_DESC -> {
                return matchByMode(node.desc, value, condition.op);
            }
            case NodeCondition.FIELD_ID -> {
                return matchByMode(node.id, value, condition.op);
            }
            case NodeCondition.FIELD_CLASS -> {
                return matchByMode(node.clazz, value, condition.op);
            }
            case NodeCondition.FIELD_LENGTH -> {
                int len = node.text == null ? 0 : node.text.length();
                int target;
                try {
                    target = Integer.parseInt(value);
                } catch (Exception e) {
                    return false;
                }
                return switch (condition.op) {
                    case 0 -> len > target;
                    case 1 -> len < target;
                    default -> len == target;
                };
            }
            case NodeCondition.FIELD_STATUS -> {
                boolean status = switch (value) {
                    case "clickable" -> node.node != null && node.node.isClickable();
                    case "selected" -> node.node != null && node.node.isSelected();
                    case "scrollable" -> node.node != null && node.node.isScrollable();
                    case "enabled" -> node.node == null || node.node.isEnabled();
                    default -> node.visible;
                };
                return condition.op == 0 == status;
            }
            default -> {
                return false;
            }
        }
    }

    // 对查找结果应用附加条件（非组合模式下也生效）
    private List<NodeInfo> applyExtraConditions(TaskRunnable runnable, List<NodeInfo> children) {
        if (children == null || children.isEmpty()) return children;
        List<NodeCondition> extra = getExtraConditions(runnable);
        if (extra.isEmpty()) return children;

        int logic = getLogicMode();
        List<NodeInfo> filtered = new ArrayList<>();
        for (NodeInfo node : children) {
            boolean ok = logic == 0;
            for (NodeCondition condition : extra) {
                boolean matched = matchCondition(node, condition);
                ok = logic == 0 ? ok && matched : ok || matched;
                if (logic == 0 && !ok) break;
                if (logic == 1 && ok) break;
            }
            if (ok) filtered.add(node);
        }
        return filtered;
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

    // 匹配逻辑：0=全部满足(AND) 1=任一满足(OR)
    private int getLogicMode() {
        PinSingleSelect logic = logicPin.getValue();
        return logic == null ? 0 : logic.getIndex();
    }

    private boolean getBooleanValue(TaskRunnable runnable, Pin conditionPin) {
        return getPinValue(runnable, conditionPin) instanceof PinBoolean pinBoolean && pinBoolean.getValue();
    }

    // 把结果列表填入输出针脚，并按输出序号选择单个节点
    private void fillNodes(TaskRunnable runnable, PinList nodes, List<NodeInfo> list) {
        // 先清空上次重试的残留结果，避免 FindExecuteAction 反复重试时列表累积重复
        nodes.clear();
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

    // 匹配逻辑（AND/OR）：仅在组合查找模式下显示
    private static class LogicShowablePin extends ShowAblePin {
        public LogicShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return action.getTypeValue() == 6;
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
