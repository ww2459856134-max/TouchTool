package top.bogey.touch_tool.utils.ui;

public interface IDragAbleRecycleViewAdapter {
    void swap(int from, int to);

    default boolean isLongPressDragEnabled() {
        return true;
    }
}
