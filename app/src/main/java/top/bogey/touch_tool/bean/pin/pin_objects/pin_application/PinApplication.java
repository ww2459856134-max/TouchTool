package top.bogey.touch_tool.bean.pin.pin_objects.pin_application;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.PinType;
import top.bogey.touch_tool.utils.GsonUtil;

public class PinApplication extends PinObject {
    private String packageName;
    private List<String> activityClasses;

    public PinApplication() {
        super(PinType.APP, PinSubType.SINGLE_APP_WITH_ACTIVITY);
    }

    public PinApplication(String packageName) {
        this();
        this.packageName = packageName;
    }

    public PinApplication(PinSubType subType) {
        super(PinType.APP, subType);
    }

    public PinApplication(PinSubType subType, String packageName) {
        super(PinType.APP, subType);
        this.packageName = packageName;
    }

    public PinApplication(String packageName, String activityClass) {
        this();
        this.packageName = packageName;
        if (activityClass != null && !activityClass.isEmpty()) {
            this.activityClasses = new ArrayList<>();
            this.activityClasses.add(activityClass);
        }
    }

    public PinApplication(JsonObject jsonObject) {
        super(jsonObject);
        packageName = GsonUtil.getAsString(jsonObject, "packageName", null);
        activityClasses = GsonUtil.getAsObject(jsonObject, "activityClasses", TypeToken.getParameterized(ArrayList.class, String.class).getType(), null);
    }

    public String getFirstActivity() {
        return activityClasses == null || activityClasses.isEmpty() ? null : activityClasses.get(0);
    }

    @Override
    public void reset() {
        super.reset();
        packageName = null;
        activityClasses = null;
    }

    @Override
    public void sync(PinBase value) {
        if (value instanceof PinApplication pinApplication) {
            packageName = pinApplication.packageName;
            activityClasses = pinApplication.activityClasses;
        }
    }

    @NonNull
    @Override
    public String toString() {
        if (activityClasses == null || activityClasses.isEmpty()) return packageName;
        return packageName + activityClasses;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public List<String> getActivityClasses() {
        return activityClasses;
    }

    public void setActivityClasses(List<String> activityClasses) {
        this.activityClasses = activityClasses;
    }

    @Override
    public final boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PinApplication that)) return false;
        if (!super.equals(object)) return false;

        return Objects.equals(getPackageName(), that.getPackageName()) && Objects.equals(getActivityClasses(), that.getActivityClasses());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(getPackageName());
        result = 31 * result + Objects.hashCode(getActivityClasses());
        return result;
    }
}
