package top.bogey.touch_tool.bean.action.node;

import androidx.annotation.NonNull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.utils.GsonUtil;

// 查找控件的附加条件行：[字段] [运算符] [值]
// 序列化为 JSON 数组存入 PinConditionsString 针脚
public class NodeCondition {
    // 字段：0文本 1描述 2ID 3类名 4文本长度 5状态
    public static final int FIELD_TEXT = 0;
    public static final int FIELD_DESC = 1;
    public static final int FIELD_ID = 2;
    public static final int FIELD_CLASS = 3;
    public static final int FIELD_LENGTH = 4;
    public static final int FIELD_STATUS = 5;

    // 文本类字段运算符（与 node_match_mode 对应）：0完全相等 1包含 2正则 3开头匹配 4结尾匹配
    // 数值类字段运算符：0大于 1小于 2等于
    // 状态类字段运算符：0是 1否
    public int op = 1;

    // 状态类字段的值为：visible/clickable/selected/scrollable/enabled，其余字段为文本或数字
    public String value = "";

    public NodeCondition() {
    }

    public NodeCondition(int field, int op, String value) {
        this.field = field;
        this.op = op;
        this.value = value;
    }

    public int field = FIELD_TEXT;

    public boolean isEmpty() {
        if (field == FIELD_STATUS) return false;
        return value == null || value.trim().isEmpty();
    }

    public String toJson() {
        return GsonUtil.toJson(this);
    }

    public static String listToJson(List<NodeCondition> list) {
        return GsonUtil.toJson(list == null ? new ArrayList<>() : list);
    }

    public static List<NodeCondition> fromJson(String json) {
        List<NodeCondition> list = GsonUtil.getAsObject(json, (Type) new com.google.gson.reflect.TypeToken<List<NodeCondition>>() {
        }.getType(), null);
        return list == null ? new ArrayList<>() : list;
    }

    @NonNull
    @Override
    public String toString() {
        return "NodeCondition{field=" + field + ", op=" + op + ", value=" + value + "}";
    }
}
