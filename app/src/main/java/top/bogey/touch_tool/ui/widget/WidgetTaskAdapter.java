package top.bogey.touch_tool.ui.widget;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.databinding.ItemWidgetTaskBinding;

// 配置页任务列表适配器
public class WidgetTaskAdapter extends RecyclerView.Adapter<WidgetTaskAdapter.Holder> {
    // 列表项：任务 + 将绑定的触发器 id
    public static class Item {
        public final Task task;
        public final String actionId;

        public Item(Task task, String actionId) {
            this.task = task;
            this.actionId = actionId;
        }
    }

    public interface OnItemClickListener {
        void onItemClick(Item item);
    }

    private final List<Item> items;
    private final OnItemClickListener listener;

    public WidgetTaskAdapter(List<Item> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWidgetTaskBinding binding = ItemWidgetTaskBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Item item = items.get(position);
        holder.binding.taskTitle.setText(item.task.getTitle());
        holder.binding.taskSubtitle.setText(item.task.getDescription());
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class Holder extends RecyclerView.ViewHolder {
        public final ItemWidgetTaskBinding binding;

        public Holder(ItemWidgetTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
