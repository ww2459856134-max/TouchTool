package top.bogey.touch_tool.bean.action.point;

import android.annotation.SuppressLint;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.action.parent.SyncAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinValueArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinPoint;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.save.setting.SettingSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.service.super_user.SuperUser;
import top.bogey.touch_tool.ui.custom.float_view.TouchPathFloatView;
import top.bogey.touch_tool.utils.HidClient;

public class TouchPointAction extends ExecuteAction implements SyncAction {
    private final transient Pin touchPin = new Pin(new PinPoint(), R.string.pin_point);
    private final transient Pin timePin = new Pin(new PinValueArea(100, 100), R.string.touch_point_action_time, false, false, true);
    private final transient Pin offsetPin = new Pin(new PinInteger(), R.string.touch_point_action_offset);
    private final transient Pin typePin = new Pin(new PinSingleSelect(), R.string.touch_point_action_type, false, false, true);
    // 执行通道：无障碍 / HID 触摸（ESP32，地址取设置页全局配置），失败自动回退无障碍
    private final transient Pin channelPin = new NotLinkAblePin(new PinSingleSelect(R.array.touch_image_channel), R.string.touch_image_channel);

    public TouchPointAction() {
        super(ActionType.TOUCH_POINT);
        addPins(touchPin, timePin, offsetPin, typePin, channelPin);
    }

    public TouchPointAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(touchPin, timePin, offsetPin, typePin, channelPin);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        sync(runnable.getTask());
        PinPoint point = getPinValue(runnable, touchPin);
        PinValueArea time = getPinValue(runnable, timePin);
        int timeValue = time.getRandomValue();
        PinNumber<?> offset = getPinValue(runnable, offsetPin);
        PinSingleSelect type = getPinValue(runnable, typePin);
        PinSingleSelect channel = getPinValue(runnable, channelPin);

        int offsetValue = offset.intValue();
        int x = (int) (point.getValue().x - offsetValue + Math.random() * offsetValue * 2);
        int y = (int) (point.getValue().y - offsetValue + Math.random() * offsetValue * 2);

        // HID 通道：通过 ESP32 蓝牙 HID 点击，无需无障碍注入
        if (channel != null && channel.getIndex() == 1) {
            String host = HidClient.getConfigHost();
            int port = HidClient.getConfigPort();
            if (host != null) {
                android.graphics.Point p = new android.graphics.Point(x, y);
                StringBuilder hexLog = new StringBuilder();
                boolean ok = HidClient.sendGesture(host, port, java.util.Arrays.asList(p, p), Math.max(50, timeValue), hexLog);
                if (ok) {
                    runnable.sleep(timeValue);
                    TouchPathFloatView.showGesture(x, y);
                    executeNext(runnable, outPin);
                    return;
                }
                runnable.addLog(new top.bogey.touch_tool.bean.other.log.LogInfo(
                        new top.bogey.touch_tool.bean.other.log.NormalLog("[HID] 发送失败，回退无障碍: " + hexLog)), 0);
            } else {
                android.widget.Toast.makeText(MainApplication.getInstance(),
                        "[HID] 请先在 设置 中配置 ESP32 设备IP，回退无障碍", android.widget.Toast.LENGTH_SHORT).show();
            }
        }

        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (type.getIndex() == 0) {
            service.runGesture(x, y, timeValue, null);
            runnable.sleep(timeValue);
        } else {
            if (SuperUser.getInstance().isValid()) {
                SuperUser.getInstance().runCommand(String.format("input swipe %d %d %d %d %d", x, y, x, y, timeValue));
            }
            runnable.sleep(timeValue);
        }
        TouchPathFloatView.showGesture(x, y);

        executeNext(runnable, outPin);
    }

    @Override
    public void sync(Task context) {
        String[] types = MainApplication.getInstance().getResources().getStringArray(R.array.touch_point_type);
        if (SettingSaver.PERMISSION_SUPER_USER.get() == 0) {
            typePin.getValue(PinSingleSelect.class).setOptions(Collections.singletonList(types[0]));
        } else {
            typePin.getValue(PinSingleSelect.class).setOptions(List.of(types));
        }
    }
}
