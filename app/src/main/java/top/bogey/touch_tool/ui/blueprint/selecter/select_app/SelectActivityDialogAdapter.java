package top.bogey.touch_tool.ui.blueprint.selecter.select_app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinApplications;
import top.bogey.touch_tool.databinding.DialogSelectActivityItemBinding;
import top.bogey.touch_tool.utils.DisplayUtil;

public class SelectActivityDialogAdapter extends RecyclerView.Adapter<SelectActivityDialogAdapter.ViewHolder> {
    private final PinApplications applications;
    private final PinApplication application;

    private final List<SelectActivityDialog.SelectActivityInfo> activities = new ArrayList<>();

    public SelectActivityDialogAdapter(PinApplications applications, PinApplication application) {
        this.applications = applications;
        this.application = application;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DialogSelectActivityItemBinding binding = DialogSelectActivityItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.refresh(activities.get(position));
    }

    @Override
    public int getItemCount() {
        return activities.size();
    }

    public void refreshActivities(List<SelectActivityDialog.SelectActivityInfo> newActivities) {
        if (newActivities == null || newActivities.isEmpty()) {
            int size = activities.size();
            activities.clear();
            notifyItemRangeRemoved(0, size);
            return;
        }

        for (int i = activities.size() - 1; i >= 0; i--) {
            SelectActivityDialog.SelectActivityInfo info = activities.get(i);
            boolean flag = true;
            for (SelectActivityDialog.SelectActivityInfo newInfo : newActivities) {
                if (newInfo.name().equals(info.name())) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                activities.remove(i);
                notifyItemRemoved(i);
            }
        }

        for (int i = 0; i < newActivities.size(); i++) {
            SelectActivityDialog.SelectActivityInfo newInfo = newActivities.get(i);
            boolean flag = true;
            for (SelectActivityDialog.SelectActivityInfo info : activities) {
                if (info.name().equals(newInfo.name())) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                if (i > activities.size()) {
                    activities.add(newInfo);
                } else {
                    activities.add(i, newInfo);
                }
                notifyItemInserted(i);
            }
        }
    }

    private boolean isSelectedActivity(String activity) {
        List<String> classes = application.getActivityClasses();
        if (classes == null || classes.isEmpty()) return false;
        return classes.contains(activity);
    }

    private int getActivityIndex(String activity) {
        for (int i = 0; i < activities.size(); i++) {
            if (activities.get(i).name().equals(activity)) {
                return i;
            }
        }
        return -1;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final Context context;
        private final DialogSelectActivityItemBinding binding;
        private final PackageManager manager;
        private SelectActivityDialog.SelectActivityInfo info;


        public ViewHolder(@NonNull DialogSelectActivityItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            context = binding.getRoot().getContext();
            manager = context.getPackageManager();

            binding.getRoot().setOnClickListener(v -> {
                selectActivity();
                binding.getRoot().setChecked(!binding.getRoot().isChecked());
            });
        }

        public void refresh(SelectActivityDialog.SelectActivityInfo info) {
            this.info = info;

            binding.title.setText(info.label());
            binding.packageName.setText(info.name());
            if (info.activityInfo() != null) {
                Drawable icon = info.activityInfo().loadIcon(manager);
                binding.icon.setImageDrawable(icon);
            } else {
                binding.icon.setImageDrawable(null);
            }

            if (info.isLauncher()) {
                binding.tips.setText(R.string.select_app_activity_launcher);
                binding.tipsBg.setCardBackgroundColor(ColorStateList.valueOf(DisplayUtil.getAttrColor(context, com.google.android.material.R.attr.colorPrimaryVariant)));
            } else {
                if (info.activityInfo() == null || info.activityInfo().exported) {
                    binding.tips.setText(R.string.select_app_activity_exported);
                    binding.tipsBg.setCardBackgroundColor(ColorStateList.valueOf(DisplayUtil.getAttrColor(context, com.google.android.material.R.attr.colorTertiary)));
                } else {
                    binding.tips.setText(R.string.select_app_activity_not_exported);
                    binding.tipsBg.setCardBackgroundColor(ColorStateList.valueOf(DisplayUtil.getAttrColor(context, com.google.android.material.R.attr.colorOutline)));
                }
            }

            binding.getRoot().setChecked(isSelectedActivity(info.name()));
        }

        private void selectActivity() {
            if (isSelectedActivity(info.name())) {
                application.getActivityClasses().remove(info.name());
            } else {
                List<String> classes = application.getActivityClasses();
                if (classes == null) classes = new ArrayList<>();
                if (applications.isSingle()) {
                    for (String aClass : classes) {
                        int index = getActivityIndex(aClass);
                        if (index >= 0) notifyItemChanged(index);
                    }
                    classes.clear();
                }
                classes.add(info.name());
                application.setActivityClasses(classes);
            }
        }
    }
}
