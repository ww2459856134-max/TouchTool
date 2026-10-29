package top.bogey.touch_tool.bean.action.normal;

import android.graphics.Point;

import com.google.gson.JsonObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinPoint;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.DisplayUtil;

// HID 中继：向 AutoJS HID 固件（ESP32）发送 UDP 指令 0x06+JSON 手势，由其蓝牙 HID 触摸操作本机
// 协议逆向自固件：{"path":"x1,y1 x2,y2","duration":ms,"screenWidth":W,"screenHeight":H}
public class HidRelayAction extends ExecuteAction {
    private final transient Pin ipPin = new Pin(new PinString(), R.string.hid_relay_ip);
    private final transient Pin portPin = new Pin(new PinInteger(21115), R.string.hid_relay_port);
    private final transient Pin typePin = new Pin(new PinSingleSelect(R.array.hid_relay_type), R.string.hid_relay_type);
    private final transient Pin startPin = new Pin(new PinPoint(), R.string.hid_relay_start);
    private final transient Pin endPin = new Pin(new PinPoint(), R.string.hid_relay_end);
    private final transient Pin durationPin = new Pin(new PinInteger(300), R.string.hid_relay_duration);

    public HidRelayAction() {
        super(ActionType.HID_RELAY);
        addPins(ipPin, portPin, typePin, startPin, endPin, durationPin);
    }

    public HidRelayAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(ipPin, portPin, typePin, startPin, endPin, durationPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinString ip = getPinValue(runnable, ipPin);
        PinInteger port = getPinValue(runnable, portPin);
        PinSingleSelect type = getPinValue(runnable, typePin);
        PinPoint start = getPinValue(runnable, startPin);
        PinPoint end = getPinValue(runnable, endPin);
        PinInteger duration = getPinValue(runnable, durationPin);

        String host = ip.getValue();
        Point startPoint = start.getValue();
        Point endPoint = end.getValue();
        long time = Math.max(0, Math.min(60000, duration.getValue()));

        if (host == null || host.trim().isEmpty() || startPoint == null) {
            executeNext(runnable, outPin);
            return;
        }

        StringBuilder pathBuilder = new StringBuilder();
        if (type.getIndex() == 1) {
            // 滑动：起点到终点
            if (endPoint == null) endPoint = startPoint;
            pathBuilder.append(startPoint.x).append(",").append(startPoint.y)
                    .append(" ").append(endPoint.x).append(",").append(endPoint.y);
        } else {
            // 单击：一个坐标点
            pathBuilder.append(startPoint.x).append(",").append(startPoint.y);
        }

        android.util.Size screenSize = DisplayUtil.getScreenSize(MainApplication.getInstance());
        String json = "{\"path\":\"" + pathBuilder + "\",\"duration\":" + time
                + ",\"screenWidth\":" + screenSize.getWidth() + ",\"screenHeight\":" + screenSize.getHeight() + "}";

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(2000);
            byte[] payload = new byte[1 + json.getBytes().length];
            payload[0] = 0x06;
            System.arraycopy(json.getBytes(), 0, payload, 1, json.getBytes().length);
            DatagramPacket packet = new DatagramPacket(payload, payload.length,
                    InetAddress.getByName(host.trim()), port.getValue());
            socket.send(packet);
            // 等待固件按 duration 插值完成，避免连续手势重叠
            runnable.sleep((int) (time + 100));
        } catch (Exception e) {
            runnable.addLog(new top.bogey.touch_tool.bean.other.log.LogInfo(
                    new top.bogey.touch_tool.bean.other.log.NormalLog("[HID中继] 发送失败: " + e)), 0);
        }

        executeNext(runnable, outPin);
    }
}
