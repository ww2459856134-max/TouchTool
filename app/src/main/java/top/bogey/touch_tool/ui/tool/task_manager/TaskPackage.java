package top.bogey.touch_tool.ui.tool.task_manager;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.bean.task.Variable;

public class TaskPackage {
    private final Task task;
    private final Set<Variable> variables = new HashSet<>();
    private final Set<TaskPackage> taskPackages = new HashSet<>();

    public TaskPackage(Task task) {
        this.task = task;
        variables.addAll(task.getVariableReferences());

        for (Task taskReference : task.getTaskReferences()) {
            if (task.equals(taskReference)) continue;
            taskPackages.add(new TaskPackage(taskReference));
        }
    }

    public TaskPackage(TaskRecord taskRecord, Task task) {
        this.task = task;
        variables.addAll(taskRecord.getVariableReferences(task));

        for (Task taskReference : taskRecord.getTaskReferences(task)) {
            if (task.equals(taskReference)) continue;
            taskPackages.add(new TaskPackage(taskRecord, taskReference));
        }
    }

    public Task getTask() {
        return task;
    }

    public Set<Variable> getVariables() {
        return variables;
    }

    public Set<TaskPackage> getTaskPackages() {
        return taskPackages;
    }

    public String getTitle() {
        return task.getTitle();
    }

    public String getDescription() {
        return task.getDescription();
    }

    public boolean isEmpty() {
        return variables.isEmpty() && taskPackages.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TaskPackage that)) return false;
        return Objects.equals(getTask(), that.getTask());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getTask());
    }
}
