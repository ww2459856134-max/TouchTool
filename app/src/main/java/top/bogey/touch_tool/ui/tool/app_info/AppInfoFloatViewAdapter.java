package top.bogey.touch_tool.ui.tool.app_info;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.databinding.FloatAppInfoItemBinding;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.DisplayUtil;

public class AppInfoFloatViewAdapter extends RecyclerView.Adapter<AppInfoFloatViewAdapter.ViewHolder> {
    private final List<TaskInfoSummary.PackageActivity> packageActivities = new ArrayList<>();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(FloatAppInfoItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.refresh(packageActivities.get(position));
    }

    @Override
    public int getItemCount() {
        return packageActivities.size();
    }

    public void addPackageActivity(TaskInfoSummary.PackageActivity packageActivity) {
        packageActivities.add(packageActivity);
        notifyItemInserted(packageActivities.size() - 1);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final Context context;
        private final FloatAppInfoItemBinding binding;
        private final PackageManager manager;

        public ViewHolder(@NonNull FloatAppInfoItemBinding binding) {
            super(binding.getRoot());
            context = binding.getRoot().getContext();
            this.binding = binding;
            manager = context.getPackageManager();

            binding.copyPackageName.setOnClickListener(v -> {
                int index = getBindingAdapterPosition();
                TaskInfoSummary.PackageActivity packageActivity = packageActivities.get(index);
                AppUtil.copyToClipboard(context, packageActivity.packageName());
            });

            binding.copyActivityClass.setOnClickListener(v -> {
                int index = getBindingAdapterPosition();
                TaskInfoSummary.PackageActivity packageActivity = packageActivities.get(index);
                AppUtil.copyToClipboard(context, packageActivity.activityName());
            });
        }

        public void refresh(TaskInfoSummary.PackageActivity packageActivity) {
            binding.title.setText(packageActivity.activityName());
            binding.packageName.setText(packageActivity.packageName());
            PackageInfo appInfo = TaskInfoSummary.getInstance().getAppInfo(packageActivity.packageName());
            if (appInfo != null && appInfo.applicationInfo != null) {
                binding.appName.setText(appInfo.applicationInfo.loadLabel(manager));
                binding.icon.setImageDrawable(appInfo.applicationInfo.loadIcon(manager));
            }

            try {
                ActivityInfo activityInfo = manager.getActivityInfo(new ComponentName(packageActivity.packageName(), packageActivity.activityName()), 0);
                if (activityInfo.exported) {
                    binding.tips.setText(R.string.select_app_activity_exported);
                    binding.tipsBg.setCardBackgroundColor(ColorStateList.valueOf(DisplayUtil.getAttrColor(context, com.google.android.material.R.attr.colorTertiary)));
                } else {
                    binding.tips.setText(R.string.select_app_activity_not_exported);
                    binding.tipsBg.setCardBackgroundColor(ColorStateList.valueOf(DisplayUtil.getAttrColor(context, com.google.android.material.R.attr.colorOutline)));
                }
                binding.tipsBg.setVisibility(ViewGroup.VISIBLE);
            } catch (PackageManager.NameNotFoundException ignored) {
                binding.tipsBg.setVisibility(ViewGroup.GONE);
            }
        }
    }
}
