package top.bogey.touch_tool.bean.action.normal;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
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

// HID 按键：通过 ESP32 蓝牙 HID 发送系统按键（返回/主页/最近任务/音量/电源/媒体键）
// 原协议只支持触摸，这里扩展 0x07 指令字：0x07 + {"k":"BACK"}
// 需要固件支持 0x07；旧固件收不到回执只会超时一次，不影响原有触摸功能
public class HidKeyAction extends ExecuteAction {
    // 与 R.array.hid_key_type 顺序一致
    private static final String[] KEYS = {
            "BACK", "HOME", "APP_SWITCH", "POWER",
            "VOLUME_UP", "VOLUME_DOWN", "MUTE",
            "MEDIA_PLAY_PAUSE", "MEDIA_NEXT", "MEDIA_PREV",
            "ENTER", "ESC", "TAB"
    };

    private final transient Pin keyPin = new Pin(new PinSingleSelect(R.array.hid_key_type), R.string.hid_key_action_key);
    private final transient Pin repeatPin = new Pin(new PinInteger(1), R.string.hid_key_action_repeat);
    private final transient Pin intervalPin = new Pin(new PinInteger(100), R.string.hid_key_action_interval);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public HidKeyAction() {
        super(ActionType.HID_KEY);
        addPins(keyPin, repeatPin, intervalPin, successPin);
    }

    public HidKeyAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(keyPin, repeatPin, intervalPin, successPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String host = HidClient.getConfigHost();
        int port = HidClient.getConfigPort();
        if (host == null) {
            runnable.addLog(new LogInfo(new NormalLog("[HID按键] 请先在 设置 中配置 ESP32 设备IP")), 0);
            successPin.getValue(PinBoolean.class).setValue(false);
            executeNext(runnable, outPin);
            return;
        }

        PinSingleSelect keyPinValue = getPinValue(runnable, keyPin);
        int index = keyPinValue == null ? 0 : keyPinValue.getIndex();
        String key = KEYS[Math.max(0, Math.min(KEYS.length - 1, index))];

        int times = Math.max(1, ((PinInteger) getPinValue(runnable, repeatPin)).intValue());
        long interval = Math.max(0, ((PinInteger) getPinValue(runnable, intervalPin)).intValue());

        boolean allOk = true;
        for (int i = 0; i < times; i++) {
            if (i > 0 && interval > 0) runnable.sleep((int) interval);
            StringBuilder hexLog = new StringBuilder();
            boolean ok = HidClient.sendKey(host, port, key, hexLog);
            if (!ok) {
                allOk = false;
                runnable.addLog(new LogInfo(new NormalLog("[HID按键] " + key + " 发送失败: " + hexLog)), 0);
                break;
            }
        }
        if (allOk) {
            runnable.addLog(new LogInfo(new NormalLog("[HID按键] " + key + " ×" + times)), 0);
            // 给系统留一点响应时间，避免连续按键被吞
            runnable.sleep((int) Math.max(80, interval));
        } else {
            android.widget.Toast.makeText(MainApplication.getInstance(),
                    "[HID按键] 发送失败，请检查设备IP与WiFi网络", android.widget.Toast.LENGTH_SHORT).show();
        }

        successPin.getValue(PinBoolean.class).setValue(allOk);
        executeNext(runnable, outPin);
    }
}
