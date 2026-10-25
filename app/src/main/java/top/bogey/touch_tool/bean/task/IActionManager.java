package top.bogey.touch_tool.bean.task;

import java.util.List;

import top.bogey.touch_tool.bean.action.Action;

public interface IActionManager {
    void addAction(Action action);

    void removeAction(String id);

    Action getAction(String id);

    List<Action> getActions();

    List<Action> getActions(String uid);

    List<Action> getActions(Class<? extends Action> actionClass);
}
