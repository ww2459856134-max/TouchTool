package top.bogey.touch_tool.ui.tool;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import top.bogey.touch_tool.databinding.ViewToolItemBinding;
import top.bogey.touch_tool.utils.AppUtil;

public class ToolViewAdapter extends RecyclerView.Adapter<ToolViewAdapter.ViewHolder> {

    private final List<ToolItem> items;

    public ToolViewAdapter(List<ToolItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ViewToolItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.refresh(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final Context context;
        private final ViewToolItemBinding binding;
        private ToolItem item;

        public ViewHolder(@NonNull ViewToolItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            context = binding.getRoot().getContext();

            binding.getRoot().setOnClickListener(v -> ToolView.openTool(context, item.id()));
            binding.link.setOnClickListener(v -> AppUtil.copyToClipboard(context, "tt://open_tool?" + item.id()));
        }

        public void refresh(ToolItem item) {
            this.item = item;
            binding.icon.setImageResource(item.icon());
            binding.title.setText(item.name());
        }
    }
}
