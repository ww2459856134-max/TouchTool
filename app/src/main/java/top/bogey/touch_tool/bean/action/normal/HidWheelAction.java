package top.bogey.touch_tool.bean.action.normal;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.other.log.LogInfo;
import top.bogey.touch_tool.bean.other.log.NormalLog;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.HidClient;

// HID 滚轮：通过 ESP32 蓝牙 HID 的相对鼠标接口发送滚轮滚动（v1.5.0 固件新增 0x0A 指令字）
// 与触摸用的绝对指针是两个独立 HID report，互不干扰——触摸点击照常可用
// 协议：0x0A + {"d":±1,"h":±1,"n":格数,"i":间隔ms}
// 旧固件收不到只会超时一次，不影响原有功能
public class HidWheelAction extends ExecuteAction {
    // 与 R.array.hid_wheel_type 顺序一致
    private static final int[][] DIRS = {
            {1, 0},    // 上滚
            {-1, 0},   // 下滚
            {0, 1},    // 右滚
            {0, -1},   // 左滚
    };

    private final transient Pin dirPin = new Pin(new PinSingleSelect(R.array.hid_wheel_type), R.string.hid_wheel_action_dir);
    private final transient Pin ticksPin = new Pin(new PinInteger(5), R.string.hid_wheel_action_ticks);
    private final transient Pin intervalPin = new Pin(new PinInteger(40), R.string.hid_wheel_action_interval);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public HidWheelAction() {
        super(ActionType.HID_WHEEL);
        addPins(dirPin, ticksPin, intervalPin, successPin);
    }

    public HidWheelAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(dirPin, ticksPin, intervalPin, successPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String host = HidClient.getConfigHost();
        int port = HidClient.getConfigPort();
        if (host == null) {
            runnable.addLog(new LogInfo(new NormalLog("[HID滚轮] 请先在 设置 中配置 ESP32 设备IP")), 0);
            successPin.getValue(PinBoolean.class).setValue(false);
            executeNext(runnable, outPin);
            return;
        }

        PinSingleSelect dirPinValue = getPinValue(runnable, dirPin);
        int index = dirPinValue == null ? 0 : dirPinValue.getIndex();
        int[] dir = DIRS[Math.max(0, Math.min(DIRS.length - 1, index))];

        int ticks = Math.max(1, ((PinInteger) getPinValue(runnable, ticksPin)).intValue());
        long interval = Math.max(0, ((PinInteger) getPinValue(runnable, intervalPin)).intValue());

        StringBuilder hexLog = new StringBuilder();
        boolean ok = HidClient.sendWheel(host, port, dir[0], dir[1], ticks, interval, hexLog);
        if (ok) {
            runnable.addLog(new LogInfo(new NormalLog("[HID滚轮] " + dirName(index) + " ×" + ticks)), 0);
            // 给系统留一点滚动动画时间
            runnable.sleep((int) Math.max(100, interval * ticks));
        } else {
            runnable.addLog(new LogInfo(new NormalLog("[HID滚轮] 发送失败: " + hexLog)), 0);
            android.widget.Toast.makeText(top.bogey.touch_tool.MainApplication.getInstance(),
                    "[HID滚轮] 发送失败，请检查设备IP与WiFi网络", android.widget.Toast.LENGTH_SHORT).show();
        }

        successPin.getValue(PinBoolean.class).setValue(ok);
        executeNext(runnable, outPin);
    }

    private static String dirName(int index) {
        return switch (index) {
            case 1 -> "下滚";
            case 2 -> "右滚";
            case 3 -> "左滚";
            default -> "上滚";
        };
    }
}
