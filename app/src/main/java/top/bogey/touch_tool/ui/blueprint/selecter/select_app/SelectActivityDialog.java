package top.bogey.touch_tool.ui.blueprint.selecter.select_app;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.Editable;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinApplications;
import top.bogey.touch_tool.databinding.DialogSelectActivityBinding;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.listener.TextChangedListener;

@SuppressLint("ViewConstructor")
public class SelectActivityDialog extends FrameLayout {

    public SelectActivityDialog(@NonNull Context context, PinApplications applications, PinApplication application, PackageInfo info) {
        super(context);
        DialogSelectActivityBinding binding = DialogSelectActivityBinding.inflate(LayoutInflater.from(context), this, true);

        SelectActivityDialogAdapter adapter = new SelectActivityDialogAdapter(applications, application);
        binding.activityBox.setAdapter(adapter);
        List<SelectActivityInfo> activityInfoList = getActivities(applications.isShared(), applications.isShortcut(), application, info);
        adapter.refreshActivities(activityInfoList);

        binding.searchEdit.addTextChangedListener(new TextChangedListener() {
            @Override
            public void afterTextChanged(Editable s) {
                String searchString = s.toString();
                if (searchString.isEmpty()) {
                    adapter.refreshActivities(activityInfoList);
                } else {
                    List<SelectActivityInfo> newActivities = new ArrayList<>();
                    for (SelectActivityInfo activityInfo : activityInfoList) {
                        if (AppUtil.isStringContains(activityInfo.label, searchString)) newActivities.add(activityInfo);
                        else if (AppUtil.isStringContains(activityInfo.name, searchString)) newActivities.add(activityInfo);
                    }
                    adapter.refreshActivities(newActivities);
                }
            }
        });
    }

    private List<SelectActivityInfo> getActivities(boolean isShared, boolean isShortcut, PinApplication application, PackageInfo info) {
        List<SelectActivityInfo> activityInfoList = new ArrayList<>();
        PackageManager manager = getContext().getPackageManager();
        String launcherActivityName = "";
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(info.packageName);
        List<ResolveInfo> resolveInfos = manager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        if (!resolveInfos.isEmpty()) {
            launcherActivityName = resolveInfos.get(0).activityInfo.name;
        }

        if (isShared) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType("*/*");
            intent.setPackage(info.packageName);
            resolveInfos = manager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
            for (ResolveInfo resolveInfo : resolveInfos) {
                ActivityInfo activityInfo = resolveInfo.activityInfo;
                activityInfoList.add(new SelectActivityInfo(
                        activityInfo,
                        activityInfo.name,
                        String.valueOf(resolveInfo.loadLabel(manager)),
                        launcherActivityName.equals(activityInfo.name)
                ));
            }
        } else if (isShortcut) {
            for (TaskInfoSummary.ShortcutInfo shortcutInfo : TaskInfoSummary.getInstance().findShortcutInfo(info.packageName, null)) {
                ActivityInfo activityInfo = null;
                try {
                    activityInfo = manager.getActivityInfo(new ComponentName(shortcutInfo.packageName, shortcutInfo.activityClass), 0);
                } catch (Exception ignored) {

                }
                activityInfoList.add(new SelectActivityInfo(
                        activityInfo,
                        shortcutInfo.intent,
                        shortcutInfo.title,
                        false
                ));
            }
        } else {
            if (info.activities != null) {
                for (ActivityInfo activityInfo : info.activities) {
                    activityInfoList.add(new SelectActivityInfo(
                            activityInfo,
                            activityInfo.name,
                            activityInfo.loadLabel(manager).toString(),
                            launcherActivityName.equals(activityInfo.name)
                    ));
                }
            }
        }

        AppUtil.chineseSort(activityInfoList, activityInfo -> activityInfo.name);
        return sortActivityInfoList(application, activityInfoList);
    }

    @NonNull
    private static List<SelectActivityInfo> sortActivityInfoList(PinApplication application, List<SelectActivityInfo> activityInfoList) {
        List<SelectActivityInfo> resultActivityInfoList = new ArrayList<>();
        List<SelectActivityInfo> exportedActivityInfoList = new ArrayList<>();
        List<SelectActivityInfo> nonExportedActivityInfoList = new ArrayList<>();
        for (SelectActivityInfo activityInfo : activityInfoList) {
            if (application.getActivityClasses() != null && application.getActivityClasses().contains(activityInfo.name)) {
                resultActivityInfoList.add(activityInfo);
            } else if (activityInfo.activityInfo != null && activityInfo.activityInfo.exported) {
                if (activityInfo.isLauncher) {
                    exportedActivityInfoList.add(0, activityInfo);
                } else {
                    exportedActivityInfoList.add(activityInfo);
                }
            } else {
                nonExportedActivityInfoList.add(activityInfo);
            }
        }
        resultActivityInfoList.addAll(exportedActivityInfoList);
        resultActivityInfoList.addAll(nonExportedActivityInfoList);
        return resultActivityInfoList;
    }

    public record SelectActivityInfo(ActivityInfo activityInfo, String name, String label, boolean isLauncher) {
    }
}
