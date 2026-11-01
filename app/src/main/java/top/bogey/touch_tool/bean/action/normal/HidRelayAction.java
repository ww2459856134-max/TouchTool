package top.bogey.touch_tool.bean.action.normal;

import android.graphics.Point;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinPoint;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.other.log.LogInfo;
import top.bogey.touch_tool.bean.other.log.NormalLog;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.HidClient;

// HID 中继：向 AutoJS HID 固件（ESP32）发送 UDP 指令，由其蓝牙 HID 触摸操作本机
// 协议实锤（抓官方 App 包对比）：UDP 到 21115 端口，0x06 + JSON
// {"a":[x1,y1,x2,y2,...],"b":时长ms,"c":屏幕宽,"d":屏幕高}，a 为扁平坐标数组，单击=同一点重复两次
public class HidRelayAction extends ExecuteAction {
    // 与 R.array.hid_relay_type 顺序一致：单击 / 滑动 / 长按 / 拖拽 / 连点
    private static final int TYPE_CLICK = 0;
    private static final int TYPE_SWIPE = 1;
    private static final int TYPE_LONG_PRESS = 2;
    private static final int TYPE_DRAG = 3;
    private static final int TYPE_REPEAT = 4;

    private final transient Pin typePin = new Pin(new PinSingleSelect(R.array.hid_relay_type), R.string.hid_relay_type);
    private final transient Pin startPin = new Pin(new PinPoint(), R.string.hid_relay_start);
    private final transient Pin endPin = new Pin(new PinPoint(), R.string.hid_relay_end);
    private final transient Pin durationPin = new Pin(new PinInteger(300), R.string.hid_relay_duration);
    private final transient Pin repeatPin = new Pin(new PinInteger(1), R.string.hid_relay_repeat);
    private final transient Pin intervalPin = new Pin(new PinInteger(120), R.string.hid_relay_interval);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public HidRelayAction() {
        super(ActionType.HID_RELAY);
        addPins(typePin, startPin, endPin, durationPin, repeatPin, intervalPin, successPin);
    }

    public HidRelayAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(typePin, startPin, endPin, durationPin, repeatPin, intervalPin, successPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        // 设备地址取全局设置（设置页配置）
        String host = HidClient.getConfigHost();
        int port = HidClient.getConfigPort();
        PinSingleSelect type = getPinValue(runnable, typePin);
        PinPoint start = getPinValue(runnable, startPin);
        PinPoint end = getPinValue(runnable, endPin);
        PinInteger duration = getPinValue(runnable, durationPin);

        Point startPoint = start.getValue();
        Point endPoint = end.getValue();
        long time = Math.max(0, Math.min(60000, duration.getValue()));

        if (host == null || startPoint == null) {
            if (host == null) android.widget.Toast.makeText(MainApplication.getInstance(),
                    "[HID中继] 请先在 设置 中配置 ESP32 设备IP", android.widget.Toast.LENGTH_SHORT).show();
            runnable.addLog(new LogInfo(new NormalLog("[HID中继] 请先在 设置 中配置 ESP32 设备IP")), 0);
            successPin.getValue(PinBoolean.class).setValue(false);
            executeNext(runnable, outPin);
            return;
        }

        int typeIndex = type == null ? TYPE_CLICK : type.getIndex();
        int repeat = Math.max(1, ((PinInteger) getPinValue(runnable, repeatPin)).intValue());
        long interval = Math.max(0, ((PinInteger) getPinValue(runnable, intervalPin)).intValue());
        if (repeat > 1 && typeIndex != TYPE_REPEAT && typeIndex != TYPE_CLICK) repeat = 1;

        List<Point> path;
        long gestureTime = time;
        switch (typeIndex) {
            case TYPE_SWIPE -> {
                // 滑动：起点到终点，贝塞尔插值成顺滑轨迹（更像人手）
                if (endPoint == null) endPoint = startPoint;
                path = HidClient.smoothSwipe(startPoint, endPoint, 16);
                if (path == null) path = Arrays.asList(startPoint, endPoint);
            }
            case TYPE_LONG_PRESS -> {
                // 长按：按下与抬起同一点，时长由用户指定（至少 500ms 才像长按）
                gestureTime = Math.max(500, time);
                path = Arrays.asList(startPoint, startPoint);
            }
            case TYPE_DRAG -> {
                // 拖拽：先长按再移动，最后在原位停住抬起；用重复末尾点实现"按住后拖动"
                if (endPoint == null) endPoint = startPoint;
                List<Point> base = HidClient.smoothSwipe(startPoint, endPoint, 20);
                if (base == null || base.isEmpty()) base = Arrays.asList(startPoint, endPoint);
                path = new java.util.ArrayList<>(base);
                // 追加几个重复终点，让固件在终点停留一会儿再抬手，更像真实拖拽
                Point last = path.get(path.size() - 1);
                for (int i = 0; i < 3; i++) path.add(last);
                gestureTime = Math.max(400, time);
            }
            default -> {
                // 单击 / 连点：按下与抬起同一点
                path = Arrays.asList(startPoint, startPoint);
            }
        }

        boolean allOk = true;
        for (int i = 0; i < repeat; i++) {
            if (i > 0 && interval > 0) runnable.sleep((int) interval);
            StringBuilder hexLog = new StringBuilder();
            boolean ok = HidClient.sendGesture(host, port, path, gestureTime, hexLog);
            if (!ok) {
                allOk = false;
                runnable.addLog(new LogInfo(new NormalLog("[HID中继] 发送失败: " + hexLog)), 0);
                android.widget.Toast.makeText(MainApplication.getInstance(),
                        "[HID中继] 发送失败，请检查设备IP与WiFi网络", android.widget.Toast.LENGTH_SHORT).show();
                break;
            }
            if (i == 0) {
                runnable.addLog(new LogInfo(new NormalLog("[HID中继] " + host + ":" + port
                        + " len=" + (hexLog.length() / 3) + " hex=" + hexLog)), 0);
            }
            // 等待固件按 duration 插值完成，避免连续手势重叠
            runnable.sleep((int) (gestureTime + 100));
        }

        successPin.getValue(PinBoolean.class).setValue(allOk);
        executeNext(runnable, outPin);
    }
}
