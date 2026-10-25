package top.bogey.touch_tool.utils.tree;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public class ObjectTreeNodeData implements ITreeNodeData {
    private final Object data;

    public ObjectTreeNodeData(Object data) {
        this.data = data;
    }

    @Override
    public List<ITreeNodeData> getChildrenData() {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public String toString() {
        if (data == null) return "";
        return data.toString();
    }
}
