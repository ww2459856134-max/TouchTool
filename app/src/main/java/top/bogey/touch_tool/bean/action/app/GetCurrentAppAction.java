package top.bogey.touch_tool.bean.action.app;

import android.content.pm.PackageInfo;

import com.google.gson.JsonObject;

import java.util.Collections;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.service.TaskRunnable;

public class GetCurrentAppAction extends CalculateAction {
    private final transient Pin appPin = new Pin(new PinApplication(), R.string.pin_app, true);
    private final transient Pin titlePin = new Pin(new PinString(), R.string.get_current_app_action_app_name, true);

    public GetCurrentAppAction() {
        super(ActionType.GET_CURRENT_APPLICATION);
        addPins(appPin, titlePin);
    }

    public GetCurrentAppAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(appPin, titlePin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        TaskInfoSummary summary = TaskInfoSummary.getInstance();
        TaskInfoSummary.PackageActivity packageActivity = summary.getPackageActivity();
        PinApplication application = appPin.getValue(PinApplication.class);
        application.setPackageName(packageActivity.packageName());
        application.setActivityClasses(Collections.singletonList(packageActivity.activityName()));

        PackageInfo appInfo = summary.getAppInfo(packageActivity.packageName());
        if (appInfo != null && appInfo.applicationInfo != null) {
            titlePin.getValue(PinString.class).setValue(String.valueOf(appInfo.applicationInfo.loadLabel(MainApplication.getInstance().getPackageManager())));
        }
    }
}
