package top.bogey.touch_tool.utils.tree;

import java.util.Collections;
import java.util.List;

public interface ILazyTreeNodeData extends ITreeNodeData {
    List<Object> getChildrenFlags();

    @Override
    default List<ITreeNodeData> getChildrenData() {
        return Collections.emptyList();
    }
}
