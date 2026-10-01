package top.bogey.touch_tool.ui.task;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.base.Identity;
import top.bogey.touch_tool.bean.save.SearchHistorySaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.databinding.ViewTaskSearchItemBinding;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.utils.AppUtil;

public class TaskSearchItemAdapter extends RecyclerView.Adapter<TaskSearchItemAdapter.ViewHolder> {
    private final TaskView taskView;

    private String tag;
    private List<Task> tasks = new ArrayList<>();

    public TaskSearchItemAdapter(TaskView taskView) {
        this.taskView = taskView;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewTaskSearchItemBinding binding = ViewTaskSearchItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.refresh(tasks.get(position));
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public void setTasks(@NonNull String tag, List<Task> tasks) {
        this.tag = tag;
        AppUtil.chineseSort(tasks, Identity::getTitle);
        this.tasks = tasks;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewTaskSearchItemBinding binding;
        private final Context context;

        public ViewHolder(@NonNull ViewTaskSearchItemBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
            context = binding.getRoot().getContext();

            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                Task task = tasks.get(position);

                if (AppUtil.isRelease(context)) {
                    MainAccessibilityService service = MainApplication.getInstance().getService();
                    if (service == null || !service.isEnabled()) {
                        Toast.makeText(context, R.string.app_setting_enable_desc, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                NavController controller = Navigation.findNavController(MainApplication.getInstance().getActivity(), R.id.conView);
                controller.navigate(top.bogey.touch_tool.ui.task.TaskViewDirections.actionTaskToBlueprint(task.getId()));

                SearchHistorySaver.getInstance().addSearchHistory(tag);
            });
        }

        public void refresh(Task task) {
            binding.taskName.setText(task.getTitle());
            binding.taskDesc.setVisibility((task.getDescription() == null || task.getDescription().isEmpty()) ? View.GONE : View.VISIBLE);
            binding.taskDesc.setText(task.getDescription());

            binding.timeText.setText(AppUtil.formatDate(context, task.getCreateTime(), true));

            String tagString = task.getTagString();
            binding.taskTag.setText(tagString);
            binding.taskTag.setVisibility(tagString.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }
    }
}
