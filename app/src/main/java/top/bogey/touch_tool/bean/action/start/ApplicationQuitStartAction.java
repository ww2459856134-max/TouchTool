package top.bogey.touch_tool.bean.action.start;

import com.google.gson.JsonObject;

import java.util.Collections;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinApplications;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.service.TaskRunnable;

public class ApplicationQuitStartAction extends StartAction {
    private final transient Pin appsPin = new NotLinkAblePin(new PinApplications(PinSubType.MULTI_APP_WITH_ACTIVITY, MainApplication.getInstance().getString(R.string.common_package)), R.string.pin_app);
    private final transient Pin appPin = new Pin(new PinApplication(), R.string.application_start_action_app, true);

    public ApplicationQuitStartAction() {
        super(ActionType.APPLICATION_QUIT_START);
        addPins(appsPin, appPin);
    }

    public ApplicationQuitStartAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(appsPin, appPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        TaskInfoSummary handler = TaskInfoSummary.getInstance();
        TaskInfoSummary.PackageActivity packageActivity = handler.getLastPackageActivity();

        PinApplication application = new PinApplication(packageActivity.packageName());
        application.setActivityClasses(Collections.singletonList(packageActivity.activityName()));
        appPin.setValue(application);

        executeNext(runnable, executePin);
    }

    @Override
    public boolean ready() {
        TaskInfoSummary handler = TaskInfoSummary.getInstance();
        TaskInfoSummary.PackageActivity packageActivity = handler.getLastPackageActivity();

        PinApplication application = new PinApplication(packageActivity.packageName());
        application.setActivityClasses(Collections.singletonList(packageActivity.activityName()));
        return appsPin.getValue(PinApplications.class).contains(application);
    }
}
