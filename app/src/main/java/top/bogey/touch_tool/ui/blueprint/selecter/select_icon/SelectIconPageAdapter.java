package top.bogey.touch_tool.ui.blueprint.selecter.select_icon;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.bogey.touch_tool.databinding.DialogSelectIconPageBinding;
import top.bogey.touch_tool.utils.callback.BitmapResultCallback;

public class SelectIconPageAdapter extends RecyclerView.Adapter<SelectIconPageAdapter.ViewHolder> {
    private final BitmapResultCallback callback;
    private final Map<String, List<Object>> dataMap = new HashMap<>();
    final List<String> tags = new ArrayList<>();

    public SelectIconPageAdapter(BitmapResultCallback callback) {
        this.callback = callback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(DialogSelectIconPageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.refresh(tags.get(position));
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }

    public void setData(Map<String, List<Object>> dataMap) {
        this.dataMap.clear();
        this.dataMap.putAll(dataMap);

        tags.clear();
        tags.addAll(dataMap.keySet());

        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final SelectIconPageItemRecyclerViewAdapter adapter;

        public ViewHolder(@NonNull DialogSelectIconPageBinding binding) {
            super(binding.getRoot());

            adapter = new SelectIconPageItemRecyclerViewAdapter(callback);
            binding.getRoot().setAdapter(adapter);
        }

        public void refresh(String tag) {
            adapter.setData(dataMap.get(tag));
        }
    }
}
