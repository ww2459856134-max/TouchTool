package top.bogey.touch_tool.bean.action.start;

import android.location.Location;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleLineString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.service.TaskRunnable;

// 地理围栏触发器：进入或离开指定圆形区域时执行
public class GeofenceStartAction extends StartAction {
    private final transient Pin latPin = new NotLinkAblePin(new PinDouble(0), R.string.geofence_start_action_lat);
    private final transient Pin lngPin = new NotLinkAblePin(new PinDouble(0), R.string.geofence_start_action_lng);
    private final transient Pin radiusPin = new NotLinkAblePin(new PinDouble(100), R.string.geofence_start_action_radius);
    private final transient Pin typePin = new NotLinkAblePin(new PinSingleSelect(R.array.geofence_type), R.string.geofence_start_action_type);
    private final transient Pin currentLatPin = new Pin(new PinDouble(0), R.string.geofence_start_action_current_lat, true);
    private final transient Pin currentLngPin = new Pin(new PinDouble(0), R.string.geofence_start_action_current_lng, true);
    private final transient Pin distancePin = new Pin(new PinDouble(0), R.string.geofence_start_action_distance, true);

    // 上次是否在区域内，用于判定进出切换，transient 状态重启后重新学习
    private transient Boolean lastInside = null;

    public GeofenceStartAction() {
        super(ActionType.GEOFENCE_START);
        addPins(latPin, lngPin, radiusPin, typePin, currentLatPin, currentLngPin, distancePin);
    }

    public GeofenceStartAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(latPin, lngPin, radiusPin, typePin, currentLatPin, currentLngPin, distancePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        TaskInfoSummary.LocationInfo info = TaskInfoSummary.getInstance().getLocationInfo();
        if (info != null) {
            currentLatPin.getValue(PinDouble.class).setValue(info.lat());
            currentLngPin.getValue(PinDouble.class).setValue(info.lng());
            distancePin.getValue(PinDouble.class).setValue(getDistance(info));
        }
        executeNext(runnable, executePin);
    }

    @Override
    public boolean ready() {
        TaskInfoSummary.LocationInfo info = TaskInfoSummary.getInstance().getLocationInfo();
        if (info == null) return false;

        double distance = getDistance(info);
        double radius = getRadius();
        boolean inside = distance <= radius;

        // 与上次状态比较，判定是否发生进出切换
        boolean entered = lastInside != null && !lastInside && inside;
        boolean exited = lastInside != null && lastInside && !inside;
        lastInside = inside;

        String type = getTriggerType();
        if (type.equals(MainApplication.getInstance().getString(R.string.geofence_type_enter))) return entered;
        if (type.equals(MainApplication.getInstance().getString(R.string.geofence_type_exit))) return exited;
        // 任意：发生切换即触发
        return entered || exited;
    }

    // 计算当前位置到区域中心的距离（米）
    public double getDistance(TaskInfoSummary.LocationInfo info) {
        float[] results = new float[1];
        Location.distanceBetween(info.lat(), info.lng(), getLat(), getLng(), results);
        return results[0];
    }

    public double getLat() {
        return latPin.getValue(PinDouble.class).getValue();
    }

    public double getLng() {
        return lngPin.getValue(PinDouble.class).getValue();
    }

    public double getRadius() {
        return Math.max(10, radiusPin.getValue(PinDouble.class).getValue());
    }

    // 获取触发类型选项文本
    public String getTriggerType() {
        return typePin.getValue(PinSingleSelect.class).getValue();
    }

    @NonNull
    @Override
    public String toString() {
        return "GeofenceStartAction{" + getLat() + "," + getLng() + "}";
    }
}
