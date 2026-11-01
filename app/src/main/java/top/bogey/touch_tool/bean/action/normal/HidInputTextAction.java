package top.bogey.touch_tool.bean.action.normal;

import android.widget.Toast;

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
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.HidClient;

// HID 键盘输入：通过 ESP32 蓝牙 HID 逐字打字，适用于屏蔽无障碍 setText 的 App
// 扩展 0x08 指令字：0x08 + {"t":"文本","i":按键间隔ms}，需固件支持
// 仅支持 ASCII（英文/数字/符号），中文需系统输入法参与，请改用粘贴方案
public class HidInputTextAction extends ExecuteAction {
    private final transient Pin textPin = new Pin(new PinString(), R.string.hid_input_action_text);
    private final transient Pin intervalPin = new Pin(new PinInteger(30), R.string.hid_input_action_interval);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public HidInputTextAction() {
        super(ActionType.HID_INPUT_TEXT);
        addPins(textPin, intervalPin, successPin);
    }

    public HidInputTextAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(textPin, intervalPin, successPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String host = HidClient.getConfigHost();
        int port = HidClient.getConfigPort();
        String text = ((PinString) getPinValue(runnable, textPin)).getValue();

        if (host == null) {
            runnable.addLog(new LogInfo(new NormalLog("[HID输入] 请先在 设置 中配置 ESP32 设备IP")), 0);
            successPin.getValue(PinBoolean.class).setValue(false);
            executeNext(runnable, outPin);
            return;
        }
        if (text == null || text.isEmpty()) {
            runnable.addLog(new LogInfo(new NormalLog("[HID输入] 内容为空，跳过")), 0);
            successPin.getValue(PinBoolean.class).setValue(true);
            executeNext(runnable, outPin);
            return;
        }

        // 只支持 ASCII：遇到非 ASCII 直接提示，避免打出乱码
        boolean asciiOnly = text.chars().allMatch(c -> c >= 0x20 && c < 0x7F);
        if (!asciiOnly) {
            runnable.addLog(new LogInfo(new NormalLog("[HID输入] 含中文等非 ASCII 字符，HID 键盘无法输入，建议改用粘贴")), 0);
            Toast.makeText(MainApplication.getInstance(),
                    "[HID输入] 仅支持英文数字符号，中文请改用粘贴", Toast.LENGTH_SHORT).show();
        }

        long interval = Math.max(0, ((PinInteger) getPinValue(runnable, intervalPin)).intValue());
        StringBuilder hexLog = new StringBuilder();
        boolean ok = HidClient.sendText(host, port, text, interval, hexLog);
        if (ok) {
            runnable.addLog(new LogInfo(new NormalLog("[HID输入] 已输入 " + text.length() + " 个字符")), 0);
            // 等待固件逐字打完
            runnable.sleep((int) Math.min(30000, text.length() * interval + 120));
        } else {
            runnable.addLog(new LogInfo(new NormalLog("[HID输入] 发送失败: " + hexLog)), 0);
            Toast.makeText(MainApplication.getInstance(),
                    "[HID输入] 发送失败，请检查设备IP与WiFi网络", Toast.LENGTH_SHORT).show();
        }

        successPin.getValue(PinBoolean.class).setValue(ok);
        executeNext(runnable, outPin);
    }
}
