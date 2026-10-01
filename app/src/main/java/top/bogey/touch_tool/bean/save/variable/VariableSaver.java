package top.bogey.touch_tool.bean.save.variable;

import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import top.bogey.touch_tool.bean.other.Usage;
import top.bogey.touch_tool.bean.save.task.TaskSaver;
import top.bogey.touch_tool.bean.task.Variable;

public class VariableSaver {
    private static VariableSaver instance;

    public static VariableSaver getInstance() {
        synchronized (VariableSaver.class) {
            if (instance == null) {
                instance = new VariableSaver();
            }
        }
        return instance;
    }

    private final Map<String, VariableSave> saves = new HashMap<>();
    private final MMKV mmkv = MMKV.mmkvWithID("VARIABLE_DB", MMKV.SINGLE_PROCESS_MODE);
    private final Set<VariableSaveListener> listeners = new HashSet<>();

    private VariableSaver() {
        recycle();
        load();
    }

    private void load() {
        String[] keys = mmkv.allKeys();
        if (keys == null) return;

        for (String key : keys) {
            VariableSave taskSave = new VariableSave(mmkv, key);
            saves.put(key, taskSave);
        }
    }

    private void recycle() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                saves.forEach((key, save) -> save.recycle());
            }
        }, 0, 5 * 60 * 1000);
    }

    public List<Variable> getVars() {
        List<Variable> vars = new ArrayList<>();
        saves.forEach((k, v) -> vars.add(v.getVar()));
        return vars;
    }

    public Variable getVar(String id) {
        VariableSave varSave = saves.get(id);
        if (varSave == null) return null;
        return varSave.getVar();
    }

    public Variable getOriginVar(String id) {
        VariableSave varSave = saves.get(id);
        if (varSave == null) return null;
        return varSave.getOriginVar();
    }

    public void saveVar(Variable var) {
        VariableSave varSave = saves.get(var.getId());
        if (varSave == null) {
            saves.put(var.getId(), new VariableSave(mmkv, var));
            listeners.stream().filter(Objects::nonNull).forEach(v -> v.onCreate(var));
        } else {
            varSave.setVar(var);
            listeners.stream().filter(Objects::nonNull).forEach(v -> v.onUpdate(var));
        }
    }

    public void removeVar(String id) {
        VariableSave varSave = saves.remove(id);
        if (varSave == null) return;
        Variable var = varSave.getVar();
        listeners.stream().filter(Objects::nonNull).forEach(v -> v.onRemove(var));
        varSave.remove();
    }

    public List<Usage> getVarUses(String id) {
        List<Usage> usages = new ArrayList<>();
        TaskSaver.getInstance().getTasks().forEach(task -> usages.addAll(task.getVariableUses(id)));
        return usages;
    }

    public void removeVariablesTag(String tag) {
        getVars().forEach(variable -> {
            if (TaskSaver.matchTag(tag, variable.getTags())) {
                variable.removeTag(tag);
                variable.save();
            }
        });
    }

    public void addListener(VariableSaveListener listener) {
        listeners.add(listener);
    }

    public void removeListener(VariableSaveListener listener) {
        listeners.remove(listener);
    }
}
