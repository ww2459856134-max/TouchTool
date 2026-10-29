package top.bogey.touch_tool.ui.blueprint.selecter.select_action;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinPoint;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;

/**
 * 动作推荐：根据当前动作类型，推荐常见的前驱/后继动作。
 * 规则表 NEXT 定义“谁后面常接谁”；反向查询自动得到“谁前面常接谁”。
 * 同时记录用户实际连线行为（统计学习），使用越多的组合排得越靠前。
 */
public final class ActionRecommend {
    // 推荐分组最大数量
    public final static int MAX_RECOMMEND = 8;

    private final static String SP_NAME = "action_recommend";

    // 后继规则表：当前动作 → 常见的下一步动作
    private final static Map<ActionType, List<ActionType>> NEXT = new HashMap<>();

    // 针脚级推荐：按针脚值类型推荐“能产出/消费该类型”的动作
    private final static Map<Class<? extends PinBase>, List<ActionType>> BY_PIN_CLASS = new LinkedHashMap<>();

    private final static List<ActionType> DEFAULT_NEXT = List.of(
            ActionType.DELAY, ActionType.SET_VARIABLE, ActionType.IF_LOGIC, ActionType.FIND_NODE);

    static {
        put(ActionType.FIND_NODE, ActionType.SET_VARIABLE, ActionType.NODE_TOUCH, ActionType.GET_NODE_INFO, ActionType.EDITTEXT_INPUT, ActionType.DELAY);
        put(ActionType.NODE_TOUCH, ActionType.DELAY, ActionType.WAIT_IF_LOGIC, ActionType.IF_LOGIC, ActionType.SET_VARIABLE);
        put(ActionType.TOUCH, ActionType.DELAY, ActionType.WAIT_IF_LOGIC, ActionType.FIND_NODE, ActionType.IF_LOGIC);
        put(ActionType.TOUCH_POINT, ActionType.DELAY, ActionType.WAIT_IF_LOGIC, ActionType.FIND_NODE, ActionType.IF_LOGIC);
        put(ActionType.CONTINUOUS_TAP, ActionType.DELAY, ActionType.WAIT_IF_LOGIC, ActionType.IF_LOGIC);
        put(ActionType.MULTI_FINGER_SWIPE, ActionType.DELAY, ActionType.WAIT_IF_LOGIC);
        put(ActionType.FIND_IMAGE, ActionType.DELAY, ActionType.TOUCH_IMAGE, ActionType.SET_VARIABLE, ActionType.IF_LOGIC);
        put(ActionType.FIND_COLORS, ActionType.DELAY, ActionType.IF_LOGIC, ActionType.SET_VARIABLE);
        put(ActionType.TOUCH_IMAGE, ActionType.DELAY, ActionType.WAIT_IF_LOGIC, ActionType.FIND_IMAGE);
        put(ActionType.GET_IMAGE, ActionType.FIND_IMAGE, ActionType.GET_OCR_TEXT, ActionType.FIND_OCR_TEXT, ActionType.SET_VARIABLE);
        put(ActionType.GET_OCR_TEXT, ActionType.SET_VARIABLE, ActionType.SWITCH_LOGIC, ActionType.IF_LOGIC, ActionType.DELAY);
        put(ActionType.FIND_OCR_TEXT, ActionType.IF_LOGIC, ActionType.DELAY, ActionType.SET_VARIABLE);
        put(ActionType.DELAY, ActionType.FIND_NODE, ActionType.NODE_TOUCH, ActionType.IF_LOGIC, ActionType.EXECUTE_TASK);
        put(ActionType.IF_LOGIC, ActionType.DELAY, ActionType.SET_VARIABLE, ActionType.FIND_NODE, ActionType.EXECUTE_TASK);
        put(ActionType.ELSE_IF_LOGIC, ActionType.DELAY, ActionType.FIND_NODE, ActionType.SET_VARIABLE);
        put(ActionType.WAIT_IF_LOGIC, ActionType.FIND_NODE, ActionType.NODE_TOUCH, ActionType.DELAY);
        put(ActionType.SWITCH_LOGIC, ActionType.DELAY, ActionType.FIND_NODE, ActionType.SET_VARIABLE);
        put(ActionType.CHOICE_LOGIC, ActionType.DELAY, ActionType.FIND_NODE);
        put(ActionType.FOR_LOGIC, ActionType.FIND_NODE, ActionType.DELAY, ActionType.SET_VARIABLE, ActionType.LIST_GET);
        put(ActionType.WHILE_LOGIC, ActionType.FIND_NODE, ActionType.DELAY, ActionType.SET_VARIABLE);
        put(ActionType.SEQUENCE_LOGIC, ActionType.FIND_NODE, ActionType.DELAY, ActionType.NODE_TOUCH);
        put(ActionType.RANDOM_LOGIC, ActionType.DELAY, ActionType.FIND_NODE, ActionType.SET_VARIABLE);
        put(ActionType.PARALLEL_LOGIC, ActionType.FIND_NODE, ActionType.DELAY, ActionType.SET_VARIABLE, ActionType.NODE_TOUCH);
        put(ActionType.TRY_CATCH_LOGIC, ActionType.DELAY, ActionType.FIND_NODE, ActionType.SET_VARIABLE);
        put(ActionType.SET_VARIABLE, ActionType.IF_LOGIC, ActionType.DELAY, ActionType.FIND_NODE, ActionType.EXECUTE_TASK);
        put(ActionType.GET_VARIABLE, ActionType.IF_LOGIC, ActionType.DELAY, ActionType.FIND_NODE);
        put(ActionType.GET_OR_SET_VARIABLE, ActionType.IF_LOGIC, ActionType.DELAY, ActionType.FIND_NODE);
        put(ActionType.GET_NODE_INFO, ActionType.SET_VARIABLE, ActionType.IF_LOGIC, ActionType.DELAY);
        put(ActionType.IS_NODE_EXIST, ActionType.IF_LOGIC, ActionType.DELAY, ActionType.FIND_NODE);
        put(ActionType.EDITTEXT_INPUT, ActionType.DELAY, ActionType.NODE_TOUCH, ActionType.WAIT_IF_LOGIC, ActionType.FIND_NODE);
        put(ActionType.EXECUTE_TASK, ActionType.DELAY, ActionType.IF_LOGIC, ActionType.STOP_TASK);
        put(ActionType.STOP_TASK, ActionType.DELAY, ActionType.EXECUTE_TASK);
        put(ActionType.OPEN_APP, ActionType.WAIT_IN_APPLICATION, ActionType.CHECK_IN_APPLICATION, ActionType.FIND_NODE, ActionType.DELAY);
        put(ActionType.CLOSE_APP, ActionType.OPEN_APP, ActionType.DELAY);
        put(ActionType.RESTART_APP, ActionType.DELAY, ActionType.FIND_NODE);
        put(ActionType.OPEN_URI_SCHEME, ActionType.DELAY, ActionType.WAIT_IN_APPLICATION, ActionType.FIND_NODE);
        put(ActionType.GET_CURRENT_APPLICATION, ActionType.IF_LOGIC, ActionType.OPEN_APP, ActionType.DELAY);
        put(ActionType.CHECK_IN_APPLICATION, ActionType.IF_LOGIC, ActionType.OPEN_APP, ActionType.DELAY);
        put(ActionType.WAIT_IN_APPLICATION, ActionType.FIND_NODE, ActionType.DELAY);
        put(ActionType.STRING_TO_APP, ActionType.DELAY, ActionType.WAIT_IN_APPLICATION);
        put(ActionType.WECHAT_MINI_PROGRAM, ActionType.DELAY, ActionType.FIND_NODE);
        put(ActionType.LIST_FOREACH, ActionType.SET_VARIABLE, ActionType.DELAY, ActionType.FIND_NODE, ActionType.IF_LOGIC);
        put(ActionType.MAP_FOREACH, ActionType.SET_VARIABLE, ActionType.DELAY, ActionType.IF_LOGIC);
        put(ActionType.LIST_GET, ActionType.SET_VARIABLE, ActionType.IF_LOGIC, ActionType.DELAY);
        put(ActionType.LIST_APPEND, ActionType.DELAY, ActionType.SET_VARIABLE);
        put(ActionType.MAP_GET, ActionType.SET_VARIABLE, ActionType.IF_LOGIC, ActionType.DELAY);
        put(ActionType.MAP_SET, ActionType.DELAY, ActionType.SET_VARIABLE);

        // 针脚级推荐：布尔类 → 各种条件判断
        putPin(PinBoolean.class,
                ActionType.NUMBER_EQUAL, ActionType.NUMBER_GREATER, ActionType.NUMBER_LESS,
                ActionType.STRING_EQUAL, ActionType.STRING_CONTAIN,
                ActionType.BOOLEAN_AND, ActionType.BOOLEAN_OR, ActionType.BOOLEAN_NOT,
                ActionType.BOOLEAN_AND_SHORT, ActionType.BOOLEAN_OR_SHORT,
                ActionType.IS_NODE_EXIST, ActionType.CHECK_IN_APPLICATION, ActionType.LIST_CONTAIN, ActionType.MAP_CONTAIN_KEY);
        // 控件类 → 控件查询与信息获取
        putPin(PinNode.class,
                ActionType.FIND_NODE, ActionType.PICK_NODE, ActionType.GET_NODE_CHILDREN, ActionType.GET_NODE_PARENT, ActionType.GET_NODE_INFO);
        // 字符串类 → 文本来源
        putPin(PinString.class,
                ActionType.GET_OCR_TEXT, ActionType.FIND_OCR_TEXT, ActionType.READ_FROM_CLIPBOARD, ActionType.READ_FILE,
                ActionType.STRING_JOIN, ActionType.STRING_FROM_OBJECT, ActionType.STRING_LENGTH, ActionType.STRING_CONTAIN, ActionType.STRING_EQUAL);
        // 数字类 → 数值来源与运算
        putPin(PinNumber.class,
                ActionType.NUMBER_ADD, ActionType.NUMBER_SUB, ActionType.NUMBER_MUL, ActionType.NUMBER_DIV, ActionType.NUMBER_RANDOM,
                ActionType.LIST_SIZE, ActionType.STRING_LENGTH, ActionType.MAP_SIZE);
        // 图像类 → 图像来源
        putPin(PinImage.class,
                ActionType.GET_IMAGE, ActionType.READ_IMAGE_FILE, ActionType.CROP_IMAGE);
        // 坐标类 → 找图/OCR/取中心
        putPin(PinPoint.class,
                ActionType.FIND_IMAGE, ActionType.FIND_OCR_TEXT, ActionType.GET_AREA_CENTER, ActionType.CHECK_AREA_CONTAIN_POS);
        // 区域类 → 区域来源
        putPin(PinArea.class,
                ActionType.MARK_AREA, ActionType.PICK_AREA, ActionType.AREA_FROM_INT, ActionType.AREA_OFFSET);
        // 列表类 → 列表来源
        putPin(PinList.class,
                ActionType.LIST_MAKE, ActionType.LIST_SUBLIST, ActionType.LIST_DIR, ActionType.MAP_KEYS, ActionType.MAP_VALUES, ActionType.LIST_FOREACH);
        // 对象类 → 通用取值
        putPin(PinObject.class,
                ActionType.MAP_GET, ActionType.LIST_GET, ActionType.GET_VARIABLE, ActionType.STRING_FROM_OBJECT);
    }

    private static void put(ActionType key, ActionType... values) {
        NEXT.put(key, List.of(values));
    }

    private static void putPin(Class<? extends PinBase> clazz, ActionType... values) {
        BY_PIN_CLASS.put(clazz, List.of(values));
    }

    private ActionRecommend() {
    }

    private static SharedPreferences getSp() {
        return MainApplication.getInstance().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 记录一次真实连线：from 动作后面接了 to 动作。用于统计学习。
     */
    public static void recordLink(ActionType from, ActionType to) {
        if (from == null || to == null || from == to) return;
        String key = from.name() + ">" + to.name();
        SharedPreferences sp = getSp();
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply();
    }

    public static int getLinkCount(ActionType from, ActionType to) {
        if (from == null || to == null) return 0;
        return getSp().getInt(from.name() + ">" + to.name(), 0);
    }

    /**
     * 按统计次数从多到少排序（稳定：次数相同保持原顺序）。
     */
    private static List<ActionType> sortByStats(ActionType source, List<ActionType> list) {
        List<ActionType> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> Integer.compare(getLinkCount(source, b), getLinkCount(source, a)));
        return sorted;
    }

    /**
     * 推荐动作类型（动作级）。
     *
     * @param source   当前正在操作的动作（可能为 null）
     * @param lookNext true = 推荐后继（从动作的输出针脚拖出），false = 推荐前驱
     */
    public static List<ActionType> recommend(ActionType source, boolean lookNext) {
        LinkedHashSet<ActionType> result = new LinkedHashSet<>();
        if (source != null && lookNext) {
            List<ActionType> list = NEXT.get(source);
            if (list != null) result.addAll(list);
        } else if (source != null) {
            for (Map.Entry<ActionType, List<ActionType>> entry : NEXT.entrySet()) {
                if (entry.getValue().contains(source)) result.add(entry.getKey());
            }
        }
        result.addAll(DEFAULT_NEXT);
        result.remove(source);
        return cap(sortByStats(source, new ArrayList<>(result)), source);
    }

    /**
     * 推荐动作类型（针脚级）：按针脚的值类型推荐相关动作。
     * 执行针脚（PinExecute）走动作级推荐，数据针脚走针脚级推荐。
     */
    public static List<ActionType> recommendForPin(ActionType source, PinBase value, boolean lookNext) {
        if (value instanceof PinExecute) return recommend(source, lookNext);
        for (Map.Entry<Class<? extends PinBase>, List<ActionType>> entry : BY_PIN_CLASS.entrySet()) {
            if (entry.getKey().isInstance(value)) {
                return cap(new ArrayList<>(entry.getValue()), source);
            }
        }
        return recommend(source, lookNext);
    }

    /**
     * 推荐数量封顶，超出部分舍弃（保持排序后的顺序）。
     */
    private static List<ActionType> cap(List<ActionType> list, ActionType source) {
        if (list.size() <= MAX_RECOMMEND) return list;
        return new ArrayList<>(list.subList(0, MAX_RECOMMEND));
    }
}
