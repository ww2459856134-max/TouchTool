package top.bogey.touch_tool.bean.task;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TaskManager implements ITaskManager {
    private transient Task parent;
    private final List<Task> tasks = new ArrayList<>();

    public TaskManager(Task parent) {
        this.parent = parent;
    }

    public void setParent(Task parent) {
        this.parent = parent;
        for (Task t : tasks) {
            t.setParent(parent);
        }
    }

    public void setNewParent(Task parent) {
        this.parent = parent;
        List<Task> list = new ArrayList<>(tasks);
        tasks.clear();
        for (Task task : list) {
            Task copy = task.copy();
            copy.setParent(parent);
            tasks.add(copy);
        }
    }

    @Override
    public void addTask(Task task) {
        tasks.add(task);
        task.setParent(parent);
    }

    @Override
    public void removeTask(String id) {
        Task task = getTask(id);
        if (task == null) return;
        tasks.remove(task);
    }

    @Override
    public Task getTask(String id) {
        return tasks.stream().filter(task -> task.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public List<Task> getTasks() {
        return tasks;
    }

    @Override
    public List<Task> getTasks(String tag) {
        return tasks.stream().filter(task -> task.getTags().contains(tag)).collect(Collectors.toList());
    }

    @Override
    public Task upFindTask(String id) {
        if (parent.getId().equals(id)) return parent;
        Task task = getTask(id);
        if (task != null) return task;
        task = parent.getParent();
        if (task != null) return task.upFindTask(id);
        return null;
    }

    @Override
    public Task downFindTask(String id) {
        if (parent.getId().equals(id)) return parent;
        Task task = getTask(id);
        if (task != null) return task;
        for (Task t : tasks) {
            task = t.downFindTask(id);
            if (task != null) return task;
        }
        return null;
    }

    @Override
    public Task getTopParent() {
        Task task = parent.getParent();
        if (task != null) return task.getTopParent();
        return parent;
    }

    @Override
    public boolean isMyParent(String id) {
        Task task = parent.getParent();
        if (task != null) {
            if (task.getId().equals(id)) return true;
            return task.isMyParent(id);
        }
        return false;
    }
}
