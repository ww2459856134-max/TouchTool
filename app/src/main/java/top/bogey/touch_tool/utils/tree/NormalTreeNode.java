package top.bogey.touch_tool.utils.tree;

import java.util.List;

public class NormalTreeNode extends TreeNode {
    public NormalTreeNode(ITreeNodeData nodeData) {
        this.nodeData = nodeData;
        for (ITreeNodeData child : nodeData.getChildrenData()) {
            addChild(new NormalTreeNode(child));
        }
    }

    public NormalTreeNode(ITreeNodeData nodeData, List<TreeNode> children) {
        this.nodeData = nodeData;
        for (TreeNode child : children) {
            addChild(child);
        }
    }
}
