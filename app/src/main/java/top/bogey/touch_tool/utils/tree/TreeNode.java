package top.bogey.touch_tool.utils.tree;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class TreeNode {
    protected final List<TreeNode> children = new ArrayList<>();
    protected TreeNode parent;

    protected ITreeNodeData nodeData;

    protected boolean expanded = false;
    protected int depth = 0;

    @Nullable
    public ITreeNodeData getData() {
        return nodeData;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        setExpanded(expanded, false);
    }

    public void setExpanded(boolean expanded, boolean children) {
        this.expanded = expanded;
        if (children) {
            for (TreeNode child : getChildren()) {
                child.setExpanded(expanded, true);
            }
        }
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public void addChild(TreeNode node) {
        getChildren().add(node);
        node.setParent(this);
        node.setDepth(getDepth() + 1);
    }

    public void addChildren(List<TreeNode> nodes) {
        nodes.forEach(this::addChild);
    }

    public TreeNode getParent() {
        return parent;
    }

    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    public List<TreeNode> getExpandedChildren() {
        List<TreeNode> list = new ArrayList<>();
        for (TreeNode child : getChildren()) {
            list.add(child);
            if (child.isExpanded()) list.addAll(child.getExpandedChildren());
        }
        return list;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
        for (TreeNode child : getChildren()) {
            child.setDepth(depth + 1);
        }
    }

    public boolean isFirstChild() {
        return parent != null && parent.getChildren().get(0) == this;
    }

    public boolean isLastChild() {
        return parent != null && parent.getChildren().get(parent.getChildren().size() - 1) == this;
    }
}
