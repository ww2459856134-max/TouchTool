package top.bogey.touch_tool.bean.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VariableManager implements IVariableManager {
    private transient Task parent;
    private final List<Variable> vars = new ArrayList<>();

    public VariableManager(Task parent) {
        this.parent = parent;
    }

    public void setParent(Task parent) {
        this.parent = parent;
        for (Variable var : vars) {
            var.setParent(parent);
        }
    }

    public void setNewParent(Task parent) {
        this.parent = parent;
        List<Variable> list = new ArrayList<>(vars);
        vars.clear();
        for (Variable variable : list) {
            Variable copy = variable.copy();
            copy.setParent(parent);
            vars.add(copy);
        }
    }

    @Override
    public boolean addVariable(Variable variable) {
        Variable var = getVariable(variable.getId());
        if (var != null) return false;
        vars.add(variable);
        variable.setParent(parent);
        return true;
    }

    @Override
    public void removeVariable(String id) {
        Variable var = getVariable(id);
        if (var == null) return;
        vars.remove(var);
    }

    @Override
    public Variable getVariable(String id) {
        for (Variable var : vars) {
            if (Objects.equals(var.getId(), id)) {
                return var;
            }
        }
        return null;
    }

    @Override
    public List<Variable> getVariables() {
        return vars;
    }

    @Override
    public Variable upFindVariable(String id) {
        Variable var = getVariable(id);
        if (var != null) return var;
        Task task = parent.getParent();
        if (task != null) return task.upFindVariable(id);
        return null;
    }

    @Override
    public Variable downFindVariable(String id) {
        Variable var = getVariable(id);
        if (var != null) return var;
        for (Task task : parent.getTasks()) {
            var = task.downFindVariable(id);
            if (var != null) return var;
        }
        return null;
    }

    @Override
    public Variable findVariableByName(String name) {
        for (Variable var : vars) {
            if (Objects.equals(var.getTitle(), name)) {
                return var;
            }
        }
        return null;
    }
}
