package top.bogey.touch_tool.bean.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import top.bogey.touch_tool.bean.action.Action;

public class ActionManager implements IActionManager {
    private final List<Action> actions = new ArrayList<>();

    public void filterNullAction() {
        actions.removeIf(Objects::isNull);
    }

    public void newCopy() {
        List<Action> list = new ArrayList<>(actions);
        actions.clear();
        for (Action action : list) {
            Action copy = action.copy();
            actions.add(copy);
        }
    }

    @Override
    public void addAction(Action action) {
        actions.add(action);
    }

    @Override
    public void removeAction(String id) {
        Action action = getAction(id);
        if (action == null) return;
        actions.remove(action);
    }

    @Override
    public Action getAction(String id) {
        return actions.stream().filter(action -> action.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public List<Action> getActions() {
        return actions;
    }

    @Override
    public List<Action> getActions(String uid) {
        return actions.stream().filter(action -> action.getUid().equals(uid)).collect(Collectors.toList());
    }

    @Override
    public List<Action> getActions(Class<? extends Action> actionClass) {
        return actions.stream().filter(actionClass::isInstance).collect(Collectors.toList());
    }
}
