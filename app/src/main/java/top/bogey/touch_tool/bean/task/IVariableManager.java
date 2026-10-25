package top.bogey.touch_tool.bean.task;

import java.util.List;

public interface IVariableManager {
    boolean addVariable(Variable variable);

    void removeVariable(String id);

    Variable getVariable(String id);

    List<Variable> getVariables();

    Variable upFindVariable(String id);

    Variable downFindVariable(String id);

    Variable findVariableByName(String name);
}
