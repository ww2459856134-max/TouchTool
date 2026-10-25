package top.bogey.touch_tool.bean.pin.pin_objects.pin_list;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.PinType;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;

public class PinApplications extends PinList {

    public static PinApplications convertAppList(PinList pinList) {
        if (pinList instanceof PinApplications) return (PinApplications) pinList;
        PinApplications applications = new PinApplications();
        if (!pinList.getValueType().getClass().equals(PinApplication.class)) return applications;
        applications.addAll(pinList);
        return applications;
    }

    public PinApplications() {
        super(PinType.APPS, PinSubType.MULTI_APP_WITH_ACTIVITY, new PinApplication());
    }

    public PinApplications(PinSubType subType) {
        super(PinType.APPS, subType, new PinApplication());
    }

    public PinApplications(PinSubType subType, String packageName) {
        super(PinType.APPS, subType, new PinApplication());
        values.add(new PinApplication(packageName));
    }

    public PinApplications(JsonObject jsonObject) {
        super(jsonObject);
    }

    @Override
    public PinList copy() {
        PinApplications pinApplications = new PinApplications(subType);
        for (PinObject pinObject : values) {
            pinApplications.values.add((PinObject) pinObject.copy());
        }
        return pinApplications;
    }

    public List<String> getPackageNames() {
        List<String> packageNames = new ArrayList<>();
        for (PinObject value : values) {
            PinApplication application = (PinApplication) value;
            packageNames.add(application.getPackageName());
        }
        return packageNames;
    }

    @Override
    public boolean contains(Object object) {
        if (object instanceof PinApplication pinApplication) {
            List<String> activityClasses = pinApplication.getActivityClasses();

            String commonPackage = MainApplication.getInstance().getString(R.string.common_package);
            boolean isCommon = false;
            for (PinObject value : values) {
                PinApplication application = (PinApplication) value;
                if (commonPackage.equals(application.getPackageName())) {
                    isCommon = true;
                    break;
                }
            }

            // 如果比较对象没有界面，那就是它包含所有界面，这时需要包含它的也是没有界面的应用
            if (activityClasses == null || activityClasses.isEmpty()) {
                for (PinObject value : values) {
                    PinApplication application = (PinApplication) value;
                    List<String> classes = application.getActivityClasses();
                    if (isCommon) {
                        // 包含通用的情况下，其他包都是排除的，匹配上就是在排除的包中，自然是不包含
                        if (application.getPackageName().equals(pinApplication.getPackageName())) {
                            return false;
                        }
                    } else {
                        // 不在通用里，就需要包是纯应用
                        if (classes == null || classes.isEmpty()) {
                            if (application.getPackageName().equals(pinApplication.getPackageName())) {
                                return true;
                            }
                        }
                    }
                }
            } else {
                // 如果比较对象有界面，那就需要详细比较界面包含了
                for (PinObject value : values) {
                    PinApplication application = (PinApplication) value;
                    List<String> classes = application.getActivityClasses();
                    if (isCommon) {
                        // 如果是通用，其他包都是排除的，如果匹配上任意包名+界面名，就是不包含
                        if (application.getPackageName().equals(pinApplication.getPackageName())) {
                            // 如果没有界面，就是排除这个应用所有界面，直接不包含
                            if (classes == null || classes.isEmpty()) return false;
                            for (String activityClass : activityClasses) {
                                // 如果任意界面匹配上了，那么也是不包含
                                if (classes.contains(activityClass)) return false;
                            }
                        }
                    } else {
                        // 如果不是通用：当界面为空，就是包含所有界面，这时只需判断包相同就行
                        if (classes == null || classes.isEmpty()) {
                            if (application.getPackageName().equals(pinApplication.getPackageName())) {
                                return true;
                            }
                        } else {
                            // 如果界面不为空，就需要判断界面是否包含
                            if (application.getPackageName().equals(pinApplication.getPackageName())) {
                                for (String activityClass : activityClasses) {
                                    // 任意界面不包含就是不包含了
                                    if (!classes.contains(activityClass)) return false;
                                }
                                return true;
                            }
                        }
                    }
                }
            }
            // 没有被排除的应用，就是包含
            return isCommon;
        }
        return false;
    }

    public boolean isSingle() {
        return getSubType() == PinSubType.SINGLE_APP || getSubType() == PinSubType.SINGLE_APP_WITH_ACTIVITY || getSubType() == PinSubType.SINGLE_APP_WITH_EXPORT_ACTIVITY || getSubType() == PinSubType.SINGLE_ACTIVITY || getSubType() == PinSubType.SINGLE_SEND_ACTIVITY || getSubType() == PinSubType.SINGLE_SHORTCUT_ACTIVITY;
    }

    public boolean isJustApp() {
        return getSubType() == PinSubType.SINGLE_APP || getSubType() == PinSubType.MULTI_APP;
    }

    public boolean isJustActivity() {
        return getSubType() == PinSubType.SINGLE_ACTIVITY || getSubType() == PinSubType.SINGLE_SEND_ACTIVITY || getSubType() == PinSubType.SINGLE_SHORTCUT_ACTIVITY;
    }

    public boolean isShared() {
        return getSubType() == PinSubType.SINGLE_SEND_ACTIVITY || getSubType() == PinSubType.MULTI_APP_WITH_EXPORT_ACTIVITY;
    }

    public boolean isShortcut() {
        return getSubType() == PinSubType.SINGLE_SHORTCUT_ACTIVITY;
    }
}
