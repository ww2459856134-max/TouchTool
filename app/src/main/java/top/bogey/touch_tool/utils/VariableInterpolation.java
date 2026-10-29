package top.bogey.touch_tool.utils;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.save.variable.VariableSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.bean.task.Variable;

/**
 * 变量插值：把文本中的 {变量名} 替换为变量当前值。
 * <p>
 * 查找顺序：当前任务 → 上级任务（沿 parent 链）→ 全局变量库。
 * 变量值取运行时保存值（getSaveValue），任意类型变量按 toString 输出。
 * 未匹配到变量的占位符原样保留，方便发现拼写错误。
 */
public final class VariableInterpolation {
    private static final Pattern TOKEN = Pattern.compile("\\{([^{}\\n]+)}");

    private VariableInterpolation() {
    }

    public static String interpolate(@Nullable Task task, @Nullable String text) {
        if (text == null || text.isEmpty() || !text.contains("{")) return text;
        Matcher matcher = TOKEN.matcher(text);
        if (!matcher.find()) return text;
        matcher.reset();
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String value = findValue(task, name);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : matcher.group(0)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @Nullable
    public static String findValue(@Nullable Task task, String name) {
        if (name == null || name.isEmpty()) return null;
        Variable variable = upFindByTitle(task, name);
        if (variable == null) {
            for (Variable var : VariableSaver.getInstance().getVars()) {
                if (name.equals(var.getTitle())) {
                    variable = var;
                    break;
                }
            }
        }
        if (variable == null) return null;
        // 优先运行时保存值；为空或反序列化成了无值基类时，回退用变量配置值（卡片上显示的值）
        PinObject value = variable.getSaveValue();
        if (isEmptyValue(value)) value = variable.getValue();
        if (isEmptyValue(value)) return null;
        String result = value.toString();
        return result.isEmpty() ? null : result;
    }

    private static boolean isEmptyValue(@Nullable PinObject value) {
        if (value == null) return true;
        if (value.getClass().equals(PinObject.class)) return true;
        return value.toString().isEmpty();
    }

    @Nullable
    private static Variable upFindByTitle(@Nullable Task task, String name) {
        if (task == null) return null;
        for (Variable var : task.getVariables()) {
            if (name.equals(var.getTitle())) return var;
        }
        return upFindByTitle(task.getParent(), name);
    }
}
