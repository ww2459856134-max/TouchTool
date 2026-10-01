package top.bogey.touch_tool.bean.task;

import java.util.List;

public interface ITaskManager {
    void addTask(Task task);

    void removeTask(String id);

    Task getTask(String id);

    List<Task> getTasks();

    List<Task> getTasks(String tag);

    Task upFindTask(String id);

    Task downFindTask(String id);

    Task getTopParent();

    /**
     * 判断传入的任务是否为我的父任务
     *
     * @param id 任务id
     * @return 结果
     */
    boolean isMyParent(String id);
}
