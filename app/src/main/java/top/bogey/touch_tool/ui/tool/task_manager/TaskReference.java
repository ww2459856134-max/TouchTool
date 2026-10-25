package top.bogey.touch_tool.ui.tool.task_manager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.bean.task.Variable;

public class TaskReference {
    private final Map<Task, Integer> tasks = new HashMap<>();
    private final Map<Variable, Integer> variables = new HashMap<>();

    public void addTask(Task task) {
        Integer times = tasks.getOrDefault(task, 0);
        if (times == null) times = 0;
        tasks.put(task, times + 1);
    }

    public void removeTask(Task task) {
        Integer times = tasks.getOrDefault(task, 0);
        if (times == null) return;
        if (times > 1) {
            tasks.put(task, 1);
        } else {
            tasks.remove(task);
        }
    }

    public boolean isUsageTask(Task task) {
        return tasks.containsKey(task);
    }

    public int getTaskUsageTimes(Task task) {
        Integer times = tasks.getOrDefault(task, 0);
        return times == null ? 0 : times;
    }

    public Set<Task> getTasks() {
        return tasks.keySet();
    }

    public void addVariable(Variable variable) {
        Integer times = variables.getOrDefault(variable, 0);
        if (times == null) times = 0;
        variables.put(variable, times + 1);
    }

    public void removeVariable(Variable variable) {
        Integer times = variables.getOrDefault(variable, 0);
        if (times == null) return;
        if (times > 1) {
            variables.put(variable, times - 1);
        } else {
            variables.remove(variable);
        }
    }

    public boolean isUsageVariable(Variable variable) {
        return variables.containsKey(variable);
    }

    public int getVariableTimes(Variable variable) {
        Integer times = variables.getOrDefault(variable, 0);
        return times == null ? 0 : times;
    }

    public void addTaskPackage(TaskPackage taskPackage) {
        addTask(taskPackage.getTask());
        taskPackage.getVariables().forEach(this::addVariable);
        for (TaskPackage aPackage : taskPackage.getTaskPackages()) {
            addTaskPackage(aPackage);
        }
    }

    public Set<Variable> getVariables() {
        return variables.keySet();
    }

    public boolean removeTaskPackage(TaskPackage taskPackage) {
        int times = getTaskUsageTimes(taskPackage.getTask());
        if (times == 0) return false;
        removeTask(taskPackage.getTask());
        taskPackage.getVariables().forEach(this::removeVariable);
        for (TaskPackage aPackage : taskPackage.getTaskPackages()) {
            removeTaskPackage(aPackage);
        }
        return true;
    }

    public boolean isUsageTaskPackage(TaskPackage taskPackage) {
        if (isUsageTask(taskPackage.getTask())) return true;
        for (Variable variable : taskPackage.getVariables()) {
            if (isUsageVariable(variable)) return true;
        }
        for (TaskPackage aPackage : taskPackage.getTaskPackages()) {
            if (isUsageTaskPackage(aPackage)) return true;
        }
        return false;
    }
}
