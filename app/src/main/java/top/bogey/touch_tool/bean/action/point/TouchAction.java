package top.bogey.touch_tool.bean.action.point;

import android.os.Build;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinFloat;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinTouchPath;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.ui.custom.float_view.TouchPathFloatView;
import top.bogey.touch_tool.utils.EAnchor;
import top.bogey.touch_tool.utils.HidClient;

public class TouchAction extends ExecuteAction {
    private final transient Pin touchPin = new Pin(new PinTouchPath(), R.string.pin_touch);
    private final transient Pin timePin = new Pin(new PinFloat(1), R.string.touch_action_time, false, false, true);
    private final transient Pin offsetPin = new Pin(new PinInteger(), R.string.touch_action_offset);
    // 执行通道：无障碍 / HID 触摸（ESP32，取设置页全局配置），失败自动回退无障碍
    private final transient Pin channelPin = new NotLinkAblePin(new PinSingleSelect(R.array.touch_image_channel), R.string.touch_image_channel);

    public TouchAction() {
        super(ActionType.TOUCH);
        addPins(touchPin, timePin, offsetPin, channelPin);
    }

    public TouchAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(touchPin, timePin, offsetPin, channelPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinTouchPath path = getPinValue(runnable, touchPin);
        PinNumber<?> time = getPinValue(runnable, timePin);
        PinNumber<?> offset = getPinValue(runnable, offsetPin);
        PinSingleSelect channel = getPinValue(runnable, channelPin);

        int offsetValue = offset.intValue();
        int offsetX = (int) (Math.random() * offsetValue * 2 - offsetValue);
        int offsetY = (int) (Math.random() * offsetValue * 2 - offsetValue);

        // HID 通道：把路径关键帧插值成坐标序列发给 ESP32
        if (channel != null && channel.getIndex() == 1) {
            if (runHidGesture(runnable, path, time.floatValue(), offsetX, offsetY)) {
                executeNext(runnable, outPin);
                return;
            }
        }

        AtomicBoolean pause = new AtomicBoolean(true);
        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            service.runGesture(path.getStrokesList(time.floatValue(), offsetX, offsetY), result -> {
                pause.set(false);
                runnable.resume();
            });
        } else {
            service.runGesture(path.getStrokes(time.floatValue(), offsetX, offsetY), result -> {
                pause.set(false);
                runnable.resume();
            });
        }

        List<PinTouchPath.PathPart> pathParts = path.getPathParts(EAnchor.TOP_LEFT);
        pathParts.forEach(pathPart -> pathPart.offset(offsetX, offsetY));
        TouchPathFloatView.showGesture(pathParts, time.floatValue());
        if (pause.get()) runnable.await();
        executeNext(runnable, outPin);
    }

    public Pin getTouchPin() {
        return touchPin;
    }

    // 通过 ESP32 HID 执行手势路径，成功返回 true；失败回退无障碍并记日志
    private boolean runHidGesture(TaskRunnable runnable, PinTouchPath path, float timeScale, int offsetX, int offsetY) {
        String host = HidClient.getConfigHost();
        int port = HidClient.getConfigPort();
        if (host == null) {
            android.widget.Toast.makeText(MainApplication.getInstance(),
                    "[HID] 请先在 设置 中配置 ESP32 设备IP，回退无障碍", android.widget.Toast.LENGTH_SHORT).show();
            runnable.addLog(new top.bogey.touch_tool.bean.other.log.LogInfo(
                    new top.bogey.touch_tool.bean.other.log.NormalLog("[HID] 未配置设备IP，回退无障碍")), 0);
            return false;
        }
        // 手势按时间切片存储：同一根手指（点 id 相同）的点分散在连续 PathPart 中，按序聚合出每根手指的轨迹
        List<PinTouchPath.PathPart> parts = path.getPathParts(EAnchor.TOP_LEFT);
        if (parts.isEmpty()) {
            runnable.addLog(new top.bogey.touch_tool.bean.other.log.LogInfo(
                    new top.bogey.touch_tool.bean.other.log.NormalLog("[HID] 手势路径为空，回退无障碍")), 0);
            return false;
        }
        java.util.Map<Integer, List<android.graphics.Point>> fingerMap = new java.util.LinkedHashMap<>();
        long totalTime = 0;
        for (PinTouchPath.PathPart part : parts) {
            totalTime += part.getTime();
            for (PinTouchPath.PathPoint p : part.getPoints()) {
                List<android.graphics.Point> track = fingerMap.computeIfAbsent(p.getId(), k -> new java.util.ArrayList<>());
                track.add(new android.graphics.Point(p.x + offsetX, p.y + offsetY));
            }
        }
        // 取采样点最多的手指作为主轨迹（单指手势即唯一轨迹）
        List<android.graphics.Point> keyFrames = null;
        for (List<android.graphics.Point> track : fingerMap.values()) {
            if (keyFrames == null || track.size() > keyFrames.size()) keyFrames = track;
        }
        if (keyFrames == null || keyFrames.size() < 2) {
            runnable.addLog(new top.bogey.touch_tool.bean.other.log.LogInfo(
                    new top.bogey.touch_tool.bean.other.log.NormalLog("[HID] 手势关键帧不足(" + (keyFrames == null ? 0 : keyFrames.size())
                            + "个, 笔画" + parts.size() + "条)，回退无障碍")), 0);
            return false;
        }

        // 关键帧间线性插值（每段 4 个采样点），总点数限制在 120 以内（固件上限 199 对坐标）
        List<android.graphics.Point> points = new java.util.ArrayList<>();
        int perSeg = Math.max(1, Math.min(8, 120 / keyFrames.size()));
        for (int i = 0; i < keyFrames.size() - 1; i++) {
            android.graphics.Point a = keyFrames.get(i);
            android.graphics.Point b = keyFrames.get(i + 1);
            for (int s = 0; s < perSeg && points.size() < 120; s++) {
                double t = (double) s / perSeg;
                points.add(new android.graphics.Point(
                        (int) Math.round(a.x + (b.x - a.x) * t),
                        (int) Math.round(a.y + (b.y - a.y) * t)));
            }
        }
        points.add(keyFrames.get(keyFrames.size() - 1));

        // 时长取录制路径各时间片之和，除以手势速度
        long duration = totalTime > 0
                ? (long) (totalTime / Math.max(0.05f, timeScale))
                : (long) (Math.max(0.05, timeScale) * 1000);
        duration = Math.max(50, Math.min(60000, duration));
        StringBuilder hexLog = new StringBuilder();
        boolean ok = HidClient.sendGesture(host, port, points, duration, hexLog);
        if (!ok) {
            runnable.addLog(new top.bogey.touch_tool.bean.other.log.LogInfo(
                    new top.bogey.touch_tool.bean.other.log.NormalLog("[HID] 发送失败，回退无障碍: " + hexLog)), 0);
            return false;
        }
        runnable.addLog(new top.bogey.touch_tool.bean.other.log.LogInfo(
                new top.bogey.touch_tool.bean.other.log.NormalLog("[HID] 手势已发送: " + points.size() + "点 " + duration + "ms -> " + host + ":" + port)), 0);
        runnable.sleep((int) Math.min(duration + 100, 60000));
        return true;
    }
}
