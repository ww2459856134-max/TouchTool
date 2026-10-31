package top.bogey.touch_tool.bean.combo;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.node.FindNodeAction;
import top.bogey.touch_tool.bean.action.node.NodeTouchAction;
import top.bogey.touch_tool.bean.action.string.StringEqualAction;
import top.bogey.touch_tool.bean.action.variable.GetVariableAction;
import top.bogey.touch_tool.bean.action.variable.SetVariableAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.task.Variable;
import top.bogey.touch_tool.utils.GsonUtil;

// 卡片组合库：自定义组合存本地 combos.json，内置组合代码生成
public class ComboSaver {
    private static ComboSaver instance;

    public static ComboSaver getInstance() {
        synchronized (ComboSaver.class) {
            if (instance == null) instance = new ComboSaver();
            return instance;
        }
    }

    private final List<ActionCombo> combos = new ArrayList<>();

    private ComboSaver() {
        load();
    }

    private File getComboFile() {
        return new File(MainApplication.getInstance().getFilesDir(), "combos.json");
    }

    private void load() {
        combos.clear();
        File file = getComboFile();
        if (!file.exists()) return;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonArray array = com.google.gson.JsonParser.parseReader(reader).getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonObject jsonObject = array.get(i).getAsJsonObject();
                try {
                    combos.add(new ActionCombo(jsonObject));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void save() {
        File file = getComboFile();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            JsonArray array = new JsonArray();
            for (ActionCombo combo : combos) {
                if (combo.isBuiltin()) continue;
                array.add(combo.toJson());
            }
            writer.write(array.toString());
        } catch (Exception ignored) {
        }
    }

    // 全部组合（内置在前）
    public List<ActionCombo> getCombos() {
        List<ActionCombo> all = new ArrayList<>();
        all.addAll(createBuiltinCombos());
        all.addAll(combos);
        return all;
    }

    @Nullable
    public ActionCombo getComboById(String id) {
        for (ActionCombo combo : getCombos()) {
            if (combo.getId().equals(id)) return combo;
        }
        return null;
    }

    public void addCombo(ActionCombo combo) {
        // 同名覆盖，方便迭代修改自己的组合
        combos.removeIf(exist -> exist.getTitle().equals(combo.getTitle()));
        combos.add(combo);
        save();
    }

    public void removeCombo(String id) {
        combos.removeIf(combo -> combo.getId().equals(id));
        save();
    }

    // 内置黄金流程组合：代码构建，pin 链接用 directAddLink（双向），不落盘
    private List<ActionCombo> createBuiltinCombos() {
        List<ActionCombo> list = new ArrayList<>();
        try {
            list.add(builtinFindNodeTouch());
            list.add(builtinFindNodeSave());
            list.add(builtinStringEqualIf());
        } catch (Exception ignored) {
        }
        return list;
    }

    // 查找控件 → 点击控件
    private ActionCombo builtinFindNodeTouch() {
        FindNodeAction findNode = new FindNodeAction();
        NodeTouchAction touch = new NodeTouchAction();

        Pin findOut = findNode.getOutPin();
        Pin touchIn = touch.getInPin();
        if (findOut != null && touchIn != null) {
            findOut.directAddLink(touchIn);
            touchIn.directAddLink(findOut);
        }
        Pin nodeOut = findNode.getNodePin();
        Pin nodeIn = touch.getNodePin();
        if (nodeOut != null && nodeIn != null) {
            nodeOut.directAddLink(nodeIn);
            nodeIn.directAddLink(nodeOut);
        }

        List<Action> actions = new ArrayList<>();
        actions.add(findNode);
        actions.add(touch);
        return buildCombo("查找控件并点击", "找屏幕上的控件文本/ID → 直接点击。插入后填好查找条件（文本或ID）即可使用", actions, null);
    }

    // 查找控件 → 存入变量（带变量定义）
    private ActionCombo builtinFindNodeSave() {
        FindNodeAction findNode = new FindNodeAction();

        Variable variable = new Variable(new PinNode());
        variable.setTitle("目标节点");
        SetVariableAction setVariable = new SetVariableAction(variable);

        Pin nodeOut = findNode.getNodePin();
        Pin varIn = setVariable.getVarPin();
        if (nodeOut != null && varIn != null) {
            nodeOut.directAddLink(varIn);
            varIn.directAddLink(nodeOut);
        }
        Pin findOut = findNode.getOutPin();
        Pin setIn = setVariable.getInPin();
        if (findOut != null && setIn != null) {
            findOut.directAddLink(setIn);
            setIn.directAddLink(findOut);
        }

        List<Action> actions = new ArrayList<>();
        actions.add(findNode);
        actions.add(setVariable);
        List<Variable> variables = new ArrayList<>();
        variables.add(variable);
        return buildCombo("查找控件并存入变量", "黄金流程：找到的节点存进变量，后续点击/输入/判断都从该变量取节点。插入后填查找条件", actions, variables);
    }

    // 文本比较 → 如果…就（默认两段文本针脚留空待填）
    private ActionCombo builtinStringEqualIf() {
        StringEqualAction equal = new StringEqualAction();
        // 条件结果连到 If 的条件输入：IfConditionAction 的条件针脚类型 PinBoolean
        top.bogey.touch_tool.bean.action.logic.IfConditionAction ifAction = new top.bogey.touch_tool.bean.action.logic.IfConditionAction();

        Pin resultOut = equal.getResultPin();
        Pin condIn = ifAction.getConditionPin();
        if (resultOut != null && condIn != null) {
            resultOut.directAddLink(condIn);
            condIn.directAddLink(resultOut);
        }

        List<Action> actions = new ArrayList<>();
        actions.add(equal);
        actions.add(ifAction);
        return buildCombo("文本比较加条件分支", "比较两段文本（支持变量插值{变量}）→ 相等走'就'分支。插入后填两段文本", actions, null);
    }

    private ActionCombo buildCombo(String title, String desc, List<Action> actions, List<Variable> variables) {
        ActionCombo combo = new ActionCombo();
        combo.setTitle(title);
        combo.setDesc(desc);
        combo.setBuiltin(true);
        combo.getActions().addAll(actions);
        if (variables != null) combo.getVariables().addAll(variables);
        // 统一排列位置：从 (0,0) 开始竖排
        int y = 0;
        for (Action action : combo.getActions()) {
            action.setPos(0, y);
            y += 320;
        }
        return combo;
    }

    // 从一段动作里收集用到的变量 id（赋值/取值动作 + 快速存变量绑定）
    public static void collectVarIds(List<Action> actions, List<String> outIds) {
        for (Action action : actions) {
            if (action instanceof top.bogey.touch_tool.bean.action.variable.SetVariableAction) {
                outIds.add(((top.bogey.touch_tool.bean.action.variable.SetVariableAction) action).getVarId());
            } else if (action instanceof GetVariableAction) {
                outIds.add(((GetVariableAction) action).getVarId());
            } else if (action instanceof top.bogey.touch_tool.bean.action.variable.GetOrSetVariableAction) {
                outIds.add(((top.bogey.touch_tool.bean.action.variable.GetOrSetVariableAction) action).getVarId());
            }
            for (Pin pin : action.getPins()) {
                String saveVarId = pin.getSaveVarId();
                if (saveVarId != null && !saveVarId.isEmpty()) outIds.add(saveVarId);
            }
        }
    }

    // 深拷贝一组动作并重建内部连线（与外部的连线自动断开），所有 id/uid 重新生成
    public static List<Action> copyActionsWithLinks(List<Action> sources) {
        // 连线记录里存的是动作 id 和针脚 id，所以源动作必须用 id 建索引
        Map<String, Action> sourceMap = new HashMap<>();
        for (Action action : sources) {
            sourceMap.put(action.getId(), action);
        }
        Map<String, Action> copyMap = new HashMap<>();
        for (Action action : sources) {
            copyMap.put(action.getUid(), action.newCopy());
        }
        for (Action action : sources) {
            Action copy = copyMap.get(action.getUid());
            if (copy == null) continue;
            for (Pin pin : action.getPins()) {
                if (!pin.isLinked()) continue;
                Pin copyPin = copy.getPinByUid(pin.getUid());
                if (copyPin == null) continue;
                for (Map.Entry<String, String> entry : pin.getLinks().entrySet()) {
                    Action linkedAction = sourceMap.get(entry.getValue());
                    if (linkedAction == null) continue;
                    Pin linkedPin = linkedAction.getPinById(entry.getKey());
                    if (linkedPin == null) continue;
                    Action linkedCopy = copyMap.get(linkedAction.getUid());
                    if (linkedCopy == null) continue;
                    Pin linkedCopyPin = linkedCopy.getPinByUid(linkedPin.getUid());
                    if (linkedCopyPin == null) continue;
                    copyPin.directAddLink(linkedCopyPin);
                    linkedCopyPin.directAddLink(copyPin);
                }
            }
        }
        // 按 sources 原顺序输出（HashMap 无序）
        List<Action> result = new ArrayList<>();
        for (Action action : sources) {
            Action copy = copyMap.get(action.getUid());
            if (copy != null) {
                copy.setUid(UUID.randomUUID().toString());
                result.add(copy);
            }
        }
        return result;
    }

    // 把动作里的变量引用（varId / saveVarId）改写到目标任务
    public static Action replaceVarIds(Action source, Map<String, String> varIdMap) {
        if (varIdMap.isEmpty()) return source;
        try {
            // 注意 GsonUtil.toJson 返回 String，必须用 JsonParser 解析，不能用 getAsJsonObject 二次序列化
            JsonObject json = com.google.gson.JsonParser.parseString(GsonUtil.toJson(source)).getAsJsonObject();
            replaceIds(json, varIdMap);
            Action replaced = GsonUtil.getAsObject(json.toString(), Action.class, null);
            return replaced != null ? replaced : source;
        } catch (Exception e) {
            return source;
        }
    }

    private static void replaceIds(com.google.gson.JsonElement element, Map<String, String> varIdMap) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (String key : new java.util.HashSet<>(obj.keySet())) {
                // 变量动作的值针脚 uid 特殊地等于变量 id，替换 varId 时必须连 uid 一起换，否则反序列化后针脚丢失
                if (("varId".equals(key) || "saveVarId".equals(key) || "uid".equals(key)) && obj.get(key).isJsonPrimitive()) {
                    String replaced = varIdMap.get(obj.get(key).getAsString());
                    if (replaced != null) obj.addProperty(key, replaced);
                } else {
                    replaceIds(obj.get(key), varIdMap);
                }
            }
        } else if (element.isJsonArray()) {
            for (com.google.gson.JsonElement item : element.getAsJsonArray()) {
                replaceIds(item, varIdMap);
            }
        }
    }
}
