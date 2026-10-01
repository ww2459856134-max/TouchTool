package top.bogey.touch_tool.utils.ui;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class DragViewHolderHelper extends ItemTouchHelper.SimpleCallback {
    public static final int HORIZONTAL = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
    public static final int VERTICAL = ItemTouchHelper.UP | ItemTouchHelper.DOWN;

    private final IDragAbleRecycleViewAdapter adapter;

    public DragViewHolderHelper(int dragDirs, IDragAbleRecycleViewAdapter adapter) {
        super(dragDirs, 0);
        this.adapter = adapter;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return adapter.isLongPressDragEnabled();
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        adapter.swap(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

    }
}
