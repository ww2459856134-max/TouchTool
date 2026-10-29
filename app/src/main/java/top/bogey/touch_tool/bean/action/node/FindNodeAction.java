package top.bogey.touch_tool.bean.action.node;

import android.graphics.Rect;

import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;
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

/**
 * 查找控件 · P0 增强
 * <p>
 * 在原版基础上新增（原针脚顺序不变，新针脚全部追加在末尾，旧任务按位置同步不受影响）：
 * 1. 匹配模式逐项可选：文本 / ID / 类名 / 描述 各带 包含·正则·精确
 * 2. 结果排序：不排序(原遍历序) / 上→下 / 左→右 / 面积大→小 / 面积小→大
 * 3. 取第 N 个：1 基；负数从末尾取（-1 = 最后一个）；越界走 else 分支
 */
public class FindNodeAction extends FindExecuteAction {
    private final transient Pin typePin = new NotLinkAblePin(new PinSingleSelect(R.array.find_node_type), R.string.find_node_action_type);
    private final transient Pin pathPin = new TypeShowablePin(new PinNodePathString(), R.string.find_node_action_path, t -> t == 0);
    private final transient Pin fullPathPin = new TypeShowablePin(new PinBoolean(true), R.string.find_node_action_full_path, t -> t == 0);
    private final transient Pin textPin = new TypeShowablePin(new PinString(), R.string.pin_string, TypeShowablePin.TEXT_OR_MULTI);
    private final transient Pin idPin = new TypeShowablePin(new PinString(), R.string.find_node_action_id, TypeShowablePin.ID_OR_MULTI);
    private final transient Pin classPin = new TypeShowablePin(new PinString(), R.string.find_node_action_class, TypeShowablePin.CLASS_OR_MULTI);
    private final transient Pin descPin = new TypeShowablePin(new PinString(), R.string.find_node_action_node_desc, TypeShowablePin.DESC_OR_MULTI);
    private final transient Pin areaPin = new TypeShowablePin(new PinArea(), R.string.pin_area, t -> t != 0 && t != 3);
    private final transient Pin pathTextPin = new TypeShowablePin(new PinNodePathTextString(), R.string.find_node_action_regex_path, t -> t == 3);
    // ===== P0 新增针脚（追加在末尾）=====
    private final transient Pin textModePin = new TypeShowablePin(new PinSingleSelect(R.array.find_node_match_mode), R.string.find_node_pro_text_mode, TypeShowablePin.TEXT_OR_MULTI);
    private final transient Pin idModePin = new TypeShowablePin(new PinSingleSelect(R.array.find_node_match_mode), R.string.find_node_pro_id_mode, TypeShowablePin.ID_OR_MULTI);
    private final transient Pin classModePin = new TypeShowablePin(new PinSingleSelect(R.array.find_node_match_mode), R.string.find_node_pro_class_mode, TypeShowablePin.CLASS_OR_MULTI);
    private final transient Pin descModePin = new TypeShowablePin(new PinSingleSelect(R.array.find_node_match_mode), R.string.find_node_pro_desc_mode, TypeShowablePin.DESC_OR_MULTI);
    private final transient Pin sortPin = new TypeShowablePin(new PinSingleSelect(R.array.find_node_sort), R.string.find_node_pro_sort, t -> t != 0);
    private final transient Pin indexPin = new TypeShowablePin(new PinInteger(1), R.string.find_node_pro_index, t -> t != 0);
    private final transient Pin relationPin = new TypeShowablePin(new PinSingleSelect(R.array.find_node_relation), R.string.find_node_pro_relation, t -> t == 6);
    // ===== 输出 =====
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node, true);
    private final transient Pin areaOutPin = new Pin(new PinArea(), R.string.pin_area, true);
    private final transient Pin nodesPin = new TypeShowablePin(new PinList(new PinNode()), true, t -> t != 0);
    private final transient Pin countPin = new TypeShowablePin(new PinInteger(), R.string.find_node_pro_count, true, t -> t != 0);

    private static final int FIELD_TEXT = 0, FIELD_ID = 1, FIELD_CLASS = 2, FIELD_DESC = 3;

    public FindNodeAction() {
        super(ActionType.FIND_NODE);
        reAddPins(typePin, pathPin, fullPathPin, textPin, idPin, classPin, descPin, areaPin, pathTextPin,
                textModePin, idModePin, classModePin, descModePin, sortPin, indexPin, relationPin,
                nodePin, areaOutPin, nodesPin, countPin);
    }

    public FindNodeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(typePin, pathPin, fullPathPin, textPin, idPin, classPin, descPin, areaPin, pathTextPin,
                textModePin, idModePin, classModePin, descModePin, sortPin, indexPin, relationPin,
                nodePin, areaOutPin, nodesPin, countPin);
    }

    @Override
    public boolean find(TaskRunnable runnable) {
        PinSingleSelect type = typePin.getValue();
        PinList nodes = nodesPin.getValue();

        List<NodeInfo> result;
        switch (type.getIndex()) {
            case 0 -> {
                PinObject pathString = getPinValue(runnable, pathPin);
                PinNodePathString path = new PinNodePathString(pathString.toString());
                PinBoolean fullPath = getPinValue(runnable, fullPathPin);
                NodeInfo nodeInfo = path.findNode(NodeInfo.getWindows(), fullPath.getValue());
                result = new ArrayList<>();
                if (nodeInfo != null) result.add(nodeInfo);
            }
            case 1, 2 -> {
                PinArea area = getPinValue(runnable, areaPin);
                NodeInfo nodeInfo = NodeInfo.getActiveWindow();
                if (nodeInfo == null) return false;

                String keyword;
                int field;
                Pin modePin;
                if (type.getIndex() == 1) {
                    PinObject text = getPinValue(runnable, textPin);
                    keyword = text.toString();
                    field = FIELD_TEXT;
                    modePin = textModePin;
                } else {
                    PinObject id = getPinValue(runnable, idPin);
                    keyword = id.toString();
                    field = FIELD_ID;
                    modePin = idModePin;
                }
                if (keyword.isEmpty()) return false;
                int mode = modeIndex(modePin);
                result = new ArrayList<>();
                for (NodeInfo root : NodeInfo.getWindows()) {
                    result.addAll(findByField(root, field, keyword, mode, area.getValue()));
                }
            }
            case 3 -> {
                PinObject pathString = getPinValue(runnable, pathTextPin);
                PinNodePathTextString path = new PinNodePathTextString(pathString.toString());
                List<NodeInfo> findNodes = path.findNodes(NodeInfo.getWindows());
                result = findNodes == null ? new ArrayList<>() : new ArrayList<>(findNodes);
            }
            case 4 -> {
                PinArea area = getPinValue(runnable, areaPin);
                PinObject className = getPinValue(runnable, classPin);
                if (className.toString().isEmpty()) return false;
                int mode = modeIndex(classModePin);
                result = new ArrayList<>();
                for (NodeInfo root : NodeInfo.getWindows()) {
                    result.addAll(findByField(root, FIELD_CLASS, className.toString(), mode, area.getValue()));
                }
            }
            case 5 -> {
                PinArea area = getPinValue(runnable, areaPin);
                PinObject desc = getPinValue(runnable, descPin);
                if (desc.toString().isEmpty()) return false;
                int mode = modeIndex(descModePin);
                result = new ArrayList<>();
                for (NodeInfo root : NodeInfo.getWindows()) {
                    result.addAll(findByField(root, FIELD_DESC, desc.toString(), mode, area.getValue()));
                }
            }
            case 6 -> {
                // 多条件：文本/ID/类名/描述 全部生效，留空忽略
                Rect area = ((PinArea) getPinValue(runnable, areaPin)).getValue();
                PinObject text = getPinValue(runnable, textPin);
                PinObject id = getPinValue(runnable, idPin);
                PinObject className = getPinValue(runnable, classPin);
                PinObject desc = getPinValue(runnable, descPin);
                List<Condition> conditions = new ArrayList<>();
                if (!text.toString().isEmpty()) conditions.add(new Condition(FIELD_TEXT, text.toString(), modeIndex(textModePin)));
                if (!id.toString().isEmpty()) conditions.add(new Condition(FIELD_ID, id.toString(), modeIndex(idModePin)));
                if (!className.toString().isEmpty()) conditions.add(new Condition(FIELD_CLASS, className.toString(), modeIndex(classModePin)));
                if (!desc.toString().isEmpty()) conditions.add(new Condition(FIELD_DESC, desc.toString(), modeIndex(descModePin)));
                if (conditions.isEmpty()) return false;
                boolean any = relationPin.getValue(PinSingleSelect.class).getIndex() == 1;
                result = new ArrayList<>();
                for (NodeInfo root : NodeInfo.getWindows()) {
                    result.addAll(findByConditions(root, conditions, any, area));
                }
            }
            default -> {
                return false;
            }
        }

        if (result == null || result.isEmpty()) return false;
        result = sort(result);

        NodeInfo picked = pick(result);
        if (picked == null) return false;

        // 修复：重试时先清空上次结果，避免列表累积重复
        nodes.clear();
        for (NodeInfo child : result) {
            nodes.add(new PinNode(child));
            MarkTargetFloatView.showTargetArea(child.area);
        }
        nodePin.setValue(nodes.get(0));
        // 输出命中控件的区域，可直接接到找图/找色/取色的区域输入
        areaOutPin.getValue(PinArea.class).setValue(picked.area);
        countPin.getValue(PinInteger.class).setValue(result.size());
        return true;
    }

    // ==================== 统一字段匹配 ====================
    private List<NodeInfo> findByField(NodeInfo root, int field, String keyword, int mode, Rect area) {
        List<NodeInfo> out = new ArrayList<>();
        Pattern regex = null;
        if (mode == 1) {
            try {
                regex = Pattern.compile(keyword);
            } catch (Exception e) {
                return out;
            }
        }
        String lower = keyword.toLowerCase();
        ArrayDeque<NodeInfo> stack = new ArrayDeque<>();
        if (inArea(root, area)) stack.push(root);
        while (!stack.isEmpty()) {
            NodeInfo node = stack.pop();
            if (node == null) continue;
            String value = switch (field) {
                case FIELD_TEXT -> node.text;
                case FIELD_ID -> node.id;
                case FIELD_CLASS -> node.clazz;
                default -> node.desc;
            };
            if (value != null && match(value, keyword, lower, mode, regex, field)) out.add(node);
            for (NodeInfo child : node.getChildren()) {
                if (inArea(child, area)) stack.push(child);
            }
        }
        return out;
    }

    private static boolean match(String value, String keyword, String lowerKeyword, int mode, Pattern regex, int field) {
        if (mode == 1) return regex != null && regex.matcher(value).find();
        if (mode == 2) {
            if (keyword.equalsIgnoreCase(value)) return true;
            // 类名精确：支持只写简名（TextView 等同 android.widget.TextView）
            return field == FIELD_CLASS && value.endsWith("." + keyword);
        }
        return value.toLowerCase().contains(lowerKeyword);
    }

    private static boolean inArea(NodeInfo n, Rect area) {
        if (n == null || n.area == null) return false;
        if (area == null || area.isEmpty()) return true;
        return area.contains(n.area) || Rect.intersects(area, n.area);
    }

    // ==================== 多条件查找 ====================
    private static class Condition {
        final int field;
        final String keyword;
        final int mode;
        final Pattern regex;

        Condition(int field, String keyword, int mode) {
            this.field = field;
            this.keyword = keyword;
            this.mode = mode;
            Pattern p = null;
            if (mode == 1) {
                try {
                    p = Pattern.compile(keyword);
                } catch (Exception ignored) {
                }
            }
            this.regex = p;
        }

        boolean test(NodeInfo node) {
            String value = switch (field) {
                case FIELD_TEXT -> node.text;
                case FIELD_ID -> node.id;
                case FIELD_CLASS -> node.clazz;
                default -> node.desc;
            };
            if (value == null) return false;
            // 正则编译失败时该条件恒不匹配（与单条件行为一致）
            if (mode == 1 && regex == null) return false;
            return match(value, keyword, keyword.toLowerCase(), mode, regex, field);
        }
    }

    private List<NodeInfo> findByConditions(NodeInfo root, List<Condition> conditions, boolean any, Rect area) {
        List<NodeInfo> out = new ArrayList<>();
        ArrayDeque<NodeInfo> stack = new ArrayDeque<>();
        if (inArea(root, area)) stack.push(root);
        while (!stack.isEmpty()) {
            NodeInfo node = stack.pop();
            if (node == null) continue;
            int hit = 0;
            for (Condition condition : conditions) {
                if (condition.test(node)) hit++;
            }
            boolean accept = any ? hit > 0 : hit == conditions.size();
            if (accept) out.add(node);
            for (NodeInfo child : node.getChildren()) {
                if (inArea(child, area)) stack.push(child);
            }
        }
        return out;
    }

    // ==================== 排序 ====================
    private List<NodeInfo> sort(List<NodeInfo> nodes) {
        int sort = sortPin.getValue(PinSingleSelect.class).getIndex();
        if (sort == 0) return nodes; // 保持原版遍历顺序，老任务行为完全一致
        List<NodeInfo> list = new ArrayList<>(nodes);
        list.sort(switch (sort) {
            case 1 -> Comparator.<NodeInfo>comparingInt(n -> n.area.top).thenComparingInt(n -> n.area.left);
            case 2 -> Comparator.<NodeInfo>comparingInt(n -> n.area.left).thenComparingInt(n -> n.area.top);
            case 3 -> Comparator.<NodeInfo>comparingInt(n -> -areaSize(n)).thenComparingInt(n -> n.area.top);
            case 4 -> Comparator.<NodeInfo>comparingInt(n -> areaSize(n)).thenComparingInt(n -> n.area.top);
            default -> Comparator.comparingInt(n -> 0);
        });
        return list;
    }

    private static int areaSize(NodeInfo n) {
        return Math.max(0, n.area.width()) * Math.max(0, n.area.height());
    }

    // ==================== 取第 N 个 ====================
    private NodeInfo pick(List<NodeInfo> list) {
        int index = indexPin.getValue(PinInteger.class).intValue();
        if (index > 0) return index <= list.size() ? list.get(index - 1) : null;
        int fromEnd = -index;
        return fromEnd < list.size() ? list.get(list.size() - 1 - fromEnd) : null;
    }

    private int modeIndex(Pin modePin) {
        return modePin.getValue(PinSingleSelect.class).getIndex();
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

    // ---------------- ShowAblePin 子类（按类型显隐，统一实现） ----------------
    private static class TypeShowablePin extends ShowAblePin {
        static final IntPredicate TEXT_OR_MULTI = t -> t == 1 || t == 6;
        static final IntPredicate ID_OR_MULTI = t -> t == 2 || t == 6;
        static final IntPredicate CLASS_OR_MULTI = t -> t == 4 || t == 6;
        static final IntPredicate DESC_OR_MULTI = t -> t == 5 || t == 6;

        private final IntPredicate match;

        public TypeShowablePin(PinBase value, int titleId, IntPredicate match) {
            super(value, titleId);
            this.match = match;
        }

        public TypeShowablePin(PinBase value, boolean out, IntPredicate match) {
            super(value, out);
            this.match = match;
        }

        public TypeShowablePin(PinBase value, int titleId, boolean out, IntPredicate match) {
            super(value, titleId, out);
            this.match = match;
        }

        @Override
        public boolean showAble(Task context) {
            FindNodeAction action = (FindNodeAction) context.getAction(getOwnerId());
            return match.test(action.getTypeValue());
        }
    }
}
