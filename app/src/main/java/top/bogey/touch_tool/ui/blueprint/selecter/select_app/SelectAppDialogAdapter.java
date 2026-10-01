package top.bogey.touch_tool.ui.blueprint.selecter.select_app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinApplications;
import top.bogey.touch_tool.databinding.DialogSelectAppItemBinding;
import top.bogey.touch_tool.utils.callback.BooleanResultCallback;

public class SelectAppDialogAdapter extends RecyclerView.Adapter<SelectAppDialogAdapter.ViewHolder> {
    private final PinApplications applications;
    private final BooleanResultCallback callback;

    private final List<PackageInfo> apps = new ArrayList<>();

    public SelectAppDialogAdapter(PinApplications applications, BooleanResultCallback callback) {
        this.applications = applications;
        this.callback = callback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(DialogSelectAppItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.refresh(apps.get(position));
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    public void refreshApps(List<PackageInfo> newApps) {
        if (newApps == null || newApps.isEmpty()) {
            int size = apps.size();
            apps.clear();
            notifyItemRangeRemoved(0, size);
            return;
        }

        for (int i = apps.size() - 1; i >= 0; i--) {
            PackageInfo app = apps.get(i);
            boolean flag = true;
            for (PackageInfo newApp : newApps) {
                if (app.packageName.equals(newApp.packageName)) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                apps.remove(i);
                notifyItemRemoved(i);
            }
        }

        for (int i = 0; i < newApps.size(); i++) {
            PackageInfo newApp = newApps.get(i);
            boolean flag = true;
            for (PackageInfo app : apps) {
                if (app.packageName.equals(newApp.packageName)) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                if (i > apps.size()) {
                    apps.add(newApp);
                } else {
                    apps.add(i, newApp);
                }
                notifyItemInserted(i);
            }
        }
    }

    private PinApplication getSelectedApp(String packageName) {
        for (PinObject value : applications) {
            if (value instanceof PinApplication app) {
                if (packageName.equals(app.getPackageName())) {
                    return app;
                }
            }
        }
        return null;
    }

    private int getAppIndex(String packageName) {
        for (int i = 0; i < apps.size(); i++) {
            PackageInfo app = apps.get(i);
            if (packageName.equals(app.packageName)) {
                return i;
            }
        }
        return -1;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final DialogSelectAppItemBinding binding;
        private final Context context;
        private final PackageManager manager;
        private PackageInfo info;

        public ViewHolder(@NonNull DialogSelectAppItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            context = binding.getRoot().getContext();
            manager = context.getPackageManager();

            binding.getRoot().setOnClickListener(v -> {
                selectApp();
                callback.onResult(true);
                notifyItemChanged(getBindingAdapterPosition());
            });

            binding.activityButton.setOnClickListener(v -> {
                PinApplication app = getSelectedApp(info.packageName);
                if (app == null) app = new PinApplication(info.packageName);
                PinApplication finalApp = app;
                SelectActivityDialog view = new SelectActivityDialog(context, applications, finalApp, info);
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.select_app_activity)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.enter, (dialog, which) -> {
                            selectApp(finalApp);
                            callback.onResult(true);
                            notifyItemChanged(getBindingAdapterPosition());
                        })
                        .setView(view)
                        .show();
            });
        }

        public void refresh(PackageInfo info) {
            this.info = info;

            binding.pkgName.setText(info.packageName);
            if (info.packageName.equals(context.getString(R.string.common_package))) {
                binding.appName.setText(context.getString(R.string.common_name));
                binding.icon.setImageDrawable(context.getApplicationInfo().loadIcon(manager));
                binding.activityButton.setVisibility(ViewGroup.GONE);
                binding.getRoot().setCheckedIconResource(R.drawable.icon_check_circle);
            } else {
                if (info.applicationInfo != null) {
                    binding.appName.setText(info.applicationInfo.loadLabel(manager));
                    binding.icon.setImageDrawable(info.applicationInfo.loadIcon(manager));
                }
                binding.activityButton.setVisibility(applications.isJustApp() || info.activities == null || info.activities.length == 0 ? ViewGroup.GONE : ViewGroup.VISIBLE);

                PinApplication commonApp = getSelectedApp(context.getString(R.string.common_package));
                binding.getRoot().setCheckedIconResource(commonApp == null ? R.drawable.icon_check_circle : R.drawable.icon_cancel);
            }

            PinApplication app = getSelectedApp(info.packageName);

            binding.getRoot().setChecked(app != null);
            if (app == null || app.getActivityClasses() == null || app.getActivityClasses().isEmpty()) {
                binding.activityButton.setText(null);
                binding.activityButton.setIconResource(R.drawable.icon_more_horiz);
            } else {
                binding.activityButton.setText(String.valueOf(app.getActivityClasses().size()));
                binding.activityButton.setIcon(null);
            }
        }

        private void selectApp() {
            PinApplication app = getSelectedApp(info.packageName);
            if (app == null && !applications.isJustActivity()) {
                // 单选的需要取消之前选择的
                if (applications.isSingle()) {
                    for (PinObject value : applications) {
                        if (value instanceof PinApplication pinApplication) {
                            int index = getAppIndex(pinApplication.getPackageName());
                            notifyItemChanged(index);
                        }
                    }
                    applications.clear();
                }
                applications.add(new PinApplication(info.packageName));
            } else {
                applications.remove(app);
            }

            if (info.packageName.equals(context.getString(R.string.common_package))) {
                for (PinObject value : applications) {
                    if (value instanceof PinApplication pinApplication) {
                        int index = getAppIndex(pinApplication.getPackageName());
                        notifyItemChanged(index);
                    }
                }
            }
        }

        private void selectApp(PinApplication app) {
            if (app.getActivityClasses() == null || app.getActivityClasses().isEmpty()) return;
            if (applications.isSingle()) {
                for (PinObject value : applications) {
                    if (value instanceof PinApplication pinApplication) {
                        int index = getAppIndex(pinApplication.getPackageName());
                        notifyItemChanged(index);
                    }
                }
                applications.clear();
            } else {
                PinApplication selectedApp = getSelectedApp(app.getPackageName());
                if (selectedApp != null) {
                    applications.remove(selectedApp);
                }
            }
            applications.add(app);
        }
    }
}
