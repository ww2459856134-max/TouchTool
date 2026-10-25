package top.bogey.touch_tool.ui.blueprint.selecter.multi_select;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinMultiSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.databinding.DialogMultiSelectItemBinding;

public class MultiSelectDialogAdapter extends RecyclerView.Adapter<MultiSelectDialogAdapter.ViewHolder> {
    private final List<PinMultiSelect.MultiSelectObject> objects = new ArrayList<>();
    final List<PinMultiSelect.MultiSelectObject> selectObjects = new ArrayList<>();

    public MultiSelectDialogAdapter(PinMultiSelect multiSelect) {
        objects.addAll(multiSelect.getSelectObjects());
        for (PinObject object : multiSelect) {
            Object value = null;
            if (object instanceof PinString pinString) {
                value = pinString.getValue();
            } else if (object instanceof PinNumber<?> pinNumber) {
                value = pinNumber.getValue();
            }
            for (PinMultiSelect.MultiSelectObject selectObject : objects) {
                if (selectObject.value().equals(value)) {
                    selectObjects.add(selectObject);
                }
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DialogMultiSelectItemBinding binding = DialogMultiSelectItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.refresh(objects.get(position));
    }

    @Override
    public int getItemCount() {
        return objects.size();
    }

    public void refreshObjects(List<PinMultiSelect.MultiSelectObject> newObjects) {
        if (newObjects == null || newObjects.isEmpty()) {
            int size = objects.size();
            objects.clear();
            notifyItemRangeRemoved(0, size);
            return;
        }

        for (int i = objects.size() - 1; i >= 0; i--) {
            PinMultiSelect.MultiSelectObject object = objects.get(i);
            boolean flag = true;
            for (PinMultiSelect.MultiSelectObject newObject : newObjects) {
                if (Objects.equals(object.value(), newObject.value())) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                objects.remove(i);
                notifyItemRemoved(i);
            }
        }

        for (int i = 0; i < newObjects.size(); i++) {
            PinMultiSelect.MultiSelectObject newObject = newObjects.get(i);
            boolean flag = true;
            for (PinMultiSelect.MultiSelectObject object : objects) {
                if (Objects.equals(object.value(), newObject.value())) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                if (i > objects.size()) {
                    objects.add(newObject);
                } else {
                    objects.add(i, newObject);
                }
                notifyItemInserted(i);
            }
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final Context context;
        private final DialogMultiSelectItemBinding binding;
        private PinMultiSelect.MultiSelectObject selectObject;

        public ViewHolder(@NonNull DialogMultiSelectItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            context = binding.getRoot().getContext();

            binding.getRoot().setOnClickListener(v -> selectObject());
        }

        public void refresh(PinMultiSelect.MultiSelectObject selectObject) {
            this.selectObject = selectObject;
            binding.title.setText(selectObject.title());
            binding.description.setText(selectObject.description());

            binding.getRoot().setChecked(selectObjects.contains(selectObject));
        }

        private void selectObject() {
            if (selectObjects.contains(selectObject)) {
                selectObjects.remove(selectObject);
                binding.getRoot().setChecked(false);
            } else {
                selectObjects.add(selectObject);
                binding.getRoot().setChecked(true);
            }
        }
    }
}
