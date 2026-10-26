package top.bogey.touch_tool.bean.action.system;

import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Path;
import android.widget.Toast;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.FindExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

// 解锁屏幕：上滑滑过无密码锁屏、绘制图案密码，或提示用户手动解锁，然后等待解锁完成再继续
//
// 上滑模式：先自动从屏幕底部向上滑一次，然后轮询等锁屏解除（适合无密码手机）
// 图案模式：先上滑唤出图案面板，再按预存的九宫格点位（如 159 = 1-5-9）自动绘制图案
// 等待模式：弹提示请用户解锁（指纹/人脸/密码无法程序代解），解锁后自动继续
//
// 图案兼容性提醒：部分厂商锁屏禁止无障碍在锁屏界面执行手势（防扒手设计），
// 图案画了没反应属于系统限制，换"等用户解锁"模式即可。
public class UnlockScreenAction extends FindExecuteAction {
    // 模式下标，与 R.array.unlock_screen_mode 一一对应
    private static final int MODE_SWIPE = 0;
    private static final int MODE_WAIT_USER = 1;
    private static final int MODE_PATTERN = 2;

    private final transient Pin modePin = new NotLinkAblePin(new PinSingleSelect(R.array.unlock_screen_mode), R.string.unlock_screen_action_mode);
    private final transient Pin patternPin = new NotLinkAblePin(new PinString("159"), R.string.unlock_screen_action_pattern);
    private final transient Pin centerYPin = new NotLinkAblePin(new PinInteger(50), R.string.unlock_screen_action_center_y);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    // 每轮执行只自动上滑一次、画一次图案、只提示一次
    private transient TaskRunnable lastRunnable = null;
    private transient boolean swiped = false;
    private transient boolean drawn = false;
    private transient boolean tipped = false;

    public UnlockScreenAction() {
        super(ActionType.UNLOCK_SCREEN);
        // 等解锁默认 30 秒
        timeoutPin.setValue(new PinInteger(30000));
        addPins(modePin, patternPin, centerYPin, successPin);
    }

    public UnlockScreenAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(modePin, patternPin, centerYPin, successPin);
    }

    @Override
    public boolean find(TaskRunnable runnable) {
        if (runnable != lastRunnable) {
            lastRunnable = runnable;
            swiped = false;
            drawn = false;
            tipped = false;
            successPin.getValue(PinBoolean.class).setValue(false);
        }

        if (!isKeyguardLocked()) {
            successPin.getValue(PinBoolean.class).setValue(true);
            return true;
        }

        PinSingleSelect mode = getPinValue(runnable, modePin);
        if (mode.getIndex() == MODE_SWIPE) {
            if (!swiped) {
                swiped = true;
                swipeUp();
            }
        } else if (mode.getIndex() == MODE_PATTERN) {
            if (!swiped) {
                // 先上滑唤出图案面板
                swiped = true;
                swipeUp();
                runnable.sleep(800);
            } else if (!drawn) {
                // 面板出来后再画图案
                drawn = true;
                drawPattern(getPinValue(runnable, patternPin).toString(), ((PinInteger) getPinValue(runnable, centerYPin)).intValue());
            }
        } else if (!tipped) {
            tipped = true;
            AppUtil.runOnUiThread(() -> Toast.makeText(MainApplication.getInstance(), R.string.unlock_screen_action_tips, Toast.LENGTH_SHORT).show());
        }
        return !isKeyguardLocked();
    }

    // 从屏幕底部中央向上滑过锁屏
    private void swipeUp() {
        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null) return;

        int width = service.getResources().getDisplayMetrics().widthPixels;
        int height = service.getResources().getDisplayMetrics().heightPixels;

        Path path = new Path();
        path.moveTo(width / 2f, height * 0.85f);
        path.lineTo(width / 2f, height * 0.15f);

        runStrokes(service, Collections.singleton(new GestureDescription.StrokeDescription(path, 0, 400)));
    }

    // 绘制九宫格图案：点位 1-9 从上到下、从左到右排列（1=左上，9=右下）
    // 支持直接数字串（159）或逗号分隔（1,5,9），连续重复的点自动合并
    private void drawPattern(String pattern, int centerYPercent) {
        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null || pattern == null || pattern.trim().isEmpty()) return;

        List<Integer> points = parsePattern(pattern);
        if (points.isEmpty()) return;

        int width = service.getResources().getDisplayMetrics().widthPixels;
        int height = service.getResources().getDisplayMetrics().heightPixels;

        // 九宫格宽度约为屏幕宽的 63%，中心 Y 默认屏幕高度 50%（可用针脚微调）
        float cell = width * 0.21f;
        float centerY = height * Math.max(10, Math.min(90, centerYPercent)) / 100f;
        float left = width / 2f - cell;
        float top = centerY - cell;

        Path path = new Path();
        boolean first = true;
        for (int point : points) {
            if (point < 1 || point > 9) continue;
            float x = left + ((point - 1) % 3) * cell;
            float y = top + ((point - 1) / 3) * cell;
            if (first) {
                path.moveTo(x, y);
                first = false;
            } else {
                path.lineTo(x, y);
            }
        }
        if (first) return;

        runStrokes(service, Collections.singleton(new GestureDescription.StrokeDescription(path, 0, 600)));
    }

    private List<Integer> parsePattern(String pattern) {
        List<Integer> points = new ArrayList<>();
        for (String part : pattern.replaceAll("[，\\s]", ",").split(",")) {
            if (part.isEmpty()) continue;
            for (char c : part.toCharArray()) {
                int point = Character.getNumericValue(c);
                // 连续重复的点没有意义，自动合并
                if (point >= 1 && point <= 9 && (points.isEmpty() || points.get(points.size() - 1) != point)) {
                    points.add(point);
                }
            }
        }
        return points;
    }

    private void runStrokes(MainAccessibilityService service, java.util.Set<GestureDescription.StrokeDescription> strokes) {
        CountDownLatch latch = new CountDownLatch(1);
        service.runGesture(strokes, result -> latch.countDown());
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    private boolean isKeyguardLocked() {
        Context context = MainApplication.getInstance();
        KeyguardManager manager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return manager != null && manager.isKeyguardLocked();
    }
}
