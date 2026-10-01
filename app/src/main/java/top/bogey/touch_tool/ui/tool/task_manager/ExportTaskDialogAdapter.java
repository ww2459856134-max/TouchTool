package top.bogey.touch_tool.ui.tool.task_manager;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.bean.task.Variable;
import top.bogey.touch_tool.databinding.DialogTaskManagerItemBinding;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.DisplayUtil;

public class ExportTaskDialogAdapter extends RecyclerView.Adapter<ExportTaskDialogAdapter.ViewHolder> {
    private final List<TaskPackage> taskPackages = new ArrayList<>();
    private final TaskReference taskReference = new TaskReference();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DialogTaskManagerItemBinding binding = DialogTaskManagerItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.refresh(taskPackages.get(position));
    }

    public TaskRecord getTaskRecord() {
        Set<Task> tasks = new HashSet<>();
        for (Task task : taskReference.getTasks()) {
            Task copy = task.copy();
            copy.cleanVariableSave();
            tasks.add(copy);
        }

        Set<Variable> variables = new HashSet<>();
        for (Variable variable : taskReference.getVariables()) {
            Variable copy = variable.copy();
            copy.setSaveValue((PinObject) copy.getValue().copy());
            variables.add(copy);
        }

        return new TaskRecord(tasks, variables);
    }

    @Override
    public int getItemCount() {
        return taskPackages.size();
    }

    public void selectAll() {
        for (TaskPackage aPackage : taskPackages) {
            taskReference.addTaskPackage(aPackage);
        }
        notifyItemRangeChanged(0, getItemCount());
    }

    public void unselectAll() {
        for (TaskPackage aPackage : taskPackages) {
            taskReference.removeTaskPackage(aPackage);
        }
        notifyItemRangeChanged(0, getItemCount());
    }

    public void refreshTasks(List<Task> newTasks) {
        if (newTasks == null || newTasks.isEmpty()) {
            int size = taskPackages.size();
            taskPackages.clear();
            notifyItemRangeRemoved(0, size);
            return;
        }
        taskPackages.clear();
        for (Task task : newTasks) {
            taskPackages.add(new TaskPackage(task));
        }
        AppUtil.chineseSort(taskPackages, TaskPackage::getTitle);
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final Context context;
        private final DialogTaskManagerItemBinding binding;
        private TaskPackage taskPackage;

        public ViewHolder(@NonNull DialogTaskManagerItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            context = binding.getRoot().getContext();

            binding.getRoot().setOnClickListener(v -> {
                if (!taskReference.removeTaskPackage(taskPackage)) {
                    taskReference.addTaskPackage(taskPackage);
                }
                notifyItemRangeChanged(0, getItemCount());
            });
        }

        public void refresh(TaskPackage taskPackage) {
            this.taskPackage = taskPackage;

            binding.title.setText(taskPackage.getTitle());
            binding.description.setVisibility((taskPackage.getDescription() == null || taskPackage.getDescription().isEmpty()) ? View.GONE : View.VISIBLE);
            binding.description.setText(taskPackage.getDescription());

            binding.referenceCard.setVisibility(taskPackage.isEmpty() ? View.GONE : View.VISIBLE);
            binding.referenceBox.removeAllViews();
            for (TaskPackage aPackage : taskPackage.getTaskPackages()) {
                Chip chip = new Chip(context);
                chip.setText(aPackage.getTitle());
                chip.setCheckable(true);
                chip.setCheckedIconVisible(true);
                chip.setChecked(taskReference.isUsageTask(aPackage.getTask()));
                chip.setChipIconResource(R.drawable.icon_assignment);
                chip.setChipIconTint(ColorStateList.valueOf(DisplayUtil.getAttrColor(context, androidx.appcompat.R.attr.colorPrimary)));
                chip.setOnClickListener(v -> chip.setChecked(taskReference.isUsageTask(aPackage.getTask())));
                binding.referenceBox.addView(chip);
            }

            for (Variable var : taskPackage.getVariables()) {
                Chip chip = new Chip(context);
                chip.setText(var.getTitle());
                chip.setCheckable(true);
                chip.setCheckedIconVisible(true);
                chip.setChecked(taskReference.isUsageVariable(var));
                chip.setChipIconResource(R.drawable.icon_note_stack);
                chip.setChipIconTint(ColorStateList.valueOf(DisplayUtil.getAttrColor(context, androidx.appcompat.R.attr.colorPrimary)));
                chip.setOnClickListener(v -> chip.setChecked(taskReference.isUsageVariable(var)));
                binding.referenceBox.addView(chip);
            }

            int times = taskReference.getTaskUsageTimes(taskPackage.getTask());
            binding.getRoot().setChecked(times > 0);
            binding.getRoot().setEnabled(times <= 1);
            binding.getRoot().setChecked(times > 0);
        }
    }
}
