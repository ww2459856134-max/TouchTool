package top.bogey.touch_tool.bean.combo;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.task.Variable;
import top.bogey.touch_tool.utils.GsonUtil;

// 卡片组合：一段保存下来的卡片（含内部连线）+ 依赖的变量定义，可插入到任意任务复用
public class ActionCombo {
    private String id;
    private String title;
    private String desc;
    // 内置组合不可删除
    private boolean builtin;
    private List<Action> actions = new ArrayList<>();
    // 组合内动作引用到的变量定义（插入时按标题映射到目标任务：同名复用，没有则新建）
    private List<Variable> variables = new ArrayList<>();

    public ActionCombo() {
        id = UUID.randomUUID().toString();
    }

    public ActionCombo(JsonObject jsonObject) {
        id = GsonUtil.getAsString(jsonObject, "id", UUID.randomUUID().toString());
        title = GsonUtil.getAsString(jsonObject, "title", "");
        desc = GsonUtil.getAsString(jsonObject, "desc", "");
        builtin = GsonUtil.getAsBoolean(jsonObject, "builtin", false);
        actions = new ArrayList<>();
        for (com.google.gson.JsonElement element : GsonUtil.getAsJsonArray(jsonObject.get("actions"))) {
            Action action = GsonUtil.getAsObject(element.toString(), Action.class, null);
            if (action != null) actions.add(action);
        }
        variables = new ArrayList<>();
        for (com.google.gson.JsonElement element : GsonUtil.getAsJsonArray(jsonObject.get("variables"))) {
            Variable variable = GsonUtil.getAsObject(element.toString(), Variable.class, null);
            if (variable != null) variables.add(variable);
        }
    }

    public JsonObject toJson() {
        return GsonUtil.getAsJsonObject(GsonUtil.toJson(this));
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public void setBuiltin(boolean builtin) {
        this.builtin = builtin;
    }

    public List<Action> getActions() {
        return actions;
    }

    public List<Variable> getVariables() {
        return variables;
    }
}
