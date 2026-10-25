package top.bogey.touch_tool.bean.action.point;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.action.parent.SyncAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinValueArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinPoint;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.save.setting.SettingSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.service.super_user.SuperUser;
import top.bogey.touch_tool.ui.custom.float_view.TouchPathFloatView;

// 连续点击：对指定位置连续点击多次
// 每次点击可在随机偏移范围内落点，点击间隔与触摸时间在范围内随机，支持单击/长按
public class ContinuousTapAction extends ExecuteAction implements SyncAction {
    private final transient Pin touchPin = new Pin(new PinPoint(), R.string.pin_point);
    private final transient Pin repeatPin = new Pin(new PinInteger(3), R.string.touch_image_action_repeat);
    private final transient Pin intervalPin = new Pin(new PinValueArea(100, 100), R.string.touch_image_action_click_interval);
    private final transient Pin offsetPin = new Pin(new PinValueArea(0, 0), R.string.continuous_tap_action_offset);
    private final transient Pin timePin = new Pin(new PinValueArea(100, 100), R.string.touch_point_action_time, false, false, true);
    private final transient Pin typePin = new Pin(new PinSingleSelect(), R.string.touch_point_action_type, false, false, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public ContinuousTapAction() {
        super(ActionType.CONTINUOUS_TAP);
        addPins(touchPin, repeatPin, intervalPin, offsetPin, timePin, typePin, successPin);
    }

    public ContinuousTapAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(touchPin, repeatPin, intervalPin, offsetPin, timePin, typePin, successPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        sync(runnable.getTask());

        PinPoint point = getPinValue(runnable, touchPin);
        int repeat = 3;
        if (getPinValue(runnable, repeatPin) instanceof PinInteger pinInteger) {
            repeat = Math.max(1, pinInteger.getValue());
        }
        // 点击间隔：每次在最小~最大之间随机
        long interval = 100;
        if (getPinValue(runnable, intervalPin) instanceof PinValueArea area) {
            interval = Math.max(0, (long) area.getRandomValue());
        }
        // 随机偏移半径：每次点击在中心坐标 ±半径 内随机落点
        int offsetRadius = 0;
        if (getPinValue(runnable, offsetPin) instanceof PinValueArea area) {
            offsetRadius = Math.max(0, (int) area.getRandomValue());
        }
        // 触摸时间：每次按压时长在最小~最大之间随机
        int timeValue = 100;
        if (getPinValue(runnable, timePin) instanceof PinValueArea area) {
            timeValue = Math.max(30, (int) area.getRandomValue());
        }
        PinSingleSelect type = getPinValue(runnable, typePin);

        MainAccessibilityService service = MainApplication.getInstance().getService();
        successPin.getValue(PinBoolean.class).setValue(false);
        if (service == null) {
            executeNext(runnable, outPin);
            return;
        }

        for (int i = 0; i < repeat; i++) {
            if (runnable.isCurrentInterrupt()) return;

            int clickX = point.getValue().x;
            int clickY = point.getValue().y;
            if (offsetRadius > 0) {
                clickX += randomOffset(offsetRadius);
                clickY += randomOffset(offsetRadius);
            }

            if (type != null && type.getIndex() == 1) {
                // 长按：优先走 Shell 通道（与点击位置动作一致），未配置时回退无障碍手势
                if (SuperUser.getInstance().isValid()) {
                    SuperUser.getInstance().runCommand(String.format("input swipe %d %d %d %d %d", clickX, clickY, clickX, clickY, timeValue));
                } else {
                    service.runGesture(clickX, clickY, timeValue, null);
                }
            } else {
                // 单击
                service.runGesture(clickX, clickY, timeValue, null);
            }
            runnable.sleep(timeValue);
            TouchPathFloatView.showGesture(clickX, clickY);

            if (i < repeat - 1 && interval > 0) runnable.sleep(interval);
        }

        successPin.getValue(PinBoolean.class).setValue(true);
        executeNext(runnable, outPin);
    }

    // 对称随机偏移：[-radius, radius]
    private int randomOffset(int radius) {
        return (int) ((Math.random() * 2 - 1) * radius);
    }

    @Override
    public void sync(Task context) {
        // 点击方式选项：未配置 Shell 通道时只提供单击（与点击位置动作保持一致）
        String[] types = MainApplication.getInstance().getResources().getStringArray(R.array.touch_point_type);
        if (SettingSaver.PERMISSION_SUPER_USER.get() == 0) {
            typePin.getValue(PinSingleSelect.class).setOptions(Collections.singletonList(types[0]));
        } else {
            typePin.getValue(PinSingleSelect.class).setOptions(List.of(types));
        }
    }
}
