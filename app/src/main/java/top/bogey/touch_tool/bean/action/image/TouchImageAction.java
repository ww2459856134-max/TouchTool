package top.bogey.touch_tool.bean.action.image;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;

import com.google.gson.JsonObject;

import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionCheckResult;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.action.system.SwitchCaptureAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinValueArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.ui.custom.float_view.TouchPathFloatView;
import top.bogey.touch_tool.utils.DisplayUtil;

public class TouchImageAction extends ExecuteAction {
    private final transient Pin templatePin = new Pin(new PinImage(), R.string.touch_image_action_template);
    private final transient Pin delayPin = new Pin(new PinValueArea(0, 0), R.string.touch_image_action_delay, false, false, true);
    private final transient Pin typePin = new NotLinkAblePin(new PinSingleSelect(R.array.touch_image_click_type), R.string.touch_image_action_type);
    private final transient Pin durationPin = new DurationShowablePin(new PinInteger(800), R.string.touch_image_action_duration, false, false, true);
    private final transient Pin repeatPin = new RepeatShowablePin(new PinInteger(3), R.string.touch_image_action_repeat, false, false, true);
    private final transient Pin intervalPin = new IntervalShowablePin(new PinInteger(100), R.string.touch_image_action_click_interval, false, false, true);
    private final transient Pin similarityPin = new Pin(new PinInteger(80), R.string.touch_image_action_similarity);
    private final transient Pin areaPin = new Pin(new PinArea(), R.string.touch_image_action_area, false, false, false);
    private final transient Pin scalePin = new Pin(new PinSingleSelect(R.array.match_image_scale, 1), R.string.image_action_scale, false, false, true);
    private final transient Pin cannyPin = new Pin(new PinBoolean(false), R.string.image_action_canny, false, false, true);
    private final transient Pin randomPin = new Pin(new PinBoolean(), R.string.touch_image_action_offset, false, false, true);
    private final transient Pin offsetXPin = new Pin(new PinInteger(0), R.string.touch_image_action_offset_x, false, false, true);
    private final transient Pin offsetYPin = new Pin(new PinInteger(0), R.string.touch_image_action_offset_y, false, false, true);
    private final transient Pin timeoutPin = new NotLinkAblePin(new PinInteger(0), R.string.wait_if_action_timeout, false, false, true);
    private final transient Pin retryIntervalPin = new NotLinkAblePin(new PinInteger(200), R.string.find_execute_action_interval, false, false, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public TouchImageAction() {
        super(ActionType.TOUCH_IMAGE);
        addPins(templatePin, delayPin, typePin, durationPin, repeatPin, intervalPin, similarityPin, areaPin, scalePin, cannyPin, randomPin, offsetXPin, offsetYPin, timeoutPin, retryIntervalPin, elsePin);
    }

    public TouchImageAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(templatePin, delayPin, typePin, durationPin, repeatPin, intervalPin, similarityPin, areaPin, scalePin, cannyPin, randomPin, offsetXPin, offsetYPin, timeoutPin, retryIntervalPin, elsePin);
        // 兼容旧任务：旧数据里保存的 hide=false 会盖掉新默认值，这里强制归入高级页
        delayPin.setHide(true);
        cannyPin.setHide(true);
        randomPin.setHide(true);
        offsetXPin.setHide(true);
        offsetYPin.setHide(true);
        timeoutPin.setHide(true);
        retryIntervalPin.setHide(true);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        // 点击前随机延时（最小到最大之间），等待界面稳定
        PinValueArea delay = getPinValue(runnable, delayPin);
        runnable.sleep(delay.getRandomValue());

        MainAccessibilityService service = MainApplication.getInstance().getService();

        PinImage template = getPinValue(runnable, templatePin);
        PinNumber<?> similarity = getPinValue(runnable, similarityPin);
        PinArea area = getPinValue(runnable, areaPin);
        PinSingleSelect scale = getPinValue(runnable, scalePin);
        PinBoolean random = getPinValue(runnable, randomPin);
        PinBoolean canny = getPinValue(runnable, cannyPin);
        int offsetX = 0;
        if (getPinValue(runnable, offsetXPin) instanceof PinInteger pinInteger) offsetX = pinInteger.getValue();
        int offsetY = 0;
        if (getPinValue(runnable, offsetYPin) instanceof PinInteger pinInteger) offsetY = pinInteger.getValue();
        // 等待图片出现：超时时间内每间隔重新截图匹配，0 为仅匹配一次
        long timeout = 0;
        if (getPinValue(runnable, timeoutPin) instanceof PinInteger pinInteger) timeout = Math.max(0, pinInteger.getValue());
        long retryInterval = 200;
        if (getPinValue(runnable, retryIntervalPin) instanceof PinInteger pinInteger) retryInterval = Math.max(50, pinInteger.getValue());

        long startTime = System.currentTimeMillis();
        Bitmap bitmap;
        Rect rect = null;
        while (true) {
            bitmap = service.tryGetScreenShot();
            rect = DisplayUtil.matchTemplate(bitmap, template.getImage(), area.getValue(), similarity.intValue(), scale.getIndex(), canny.getValue());
            if (rect != null && !rect.isEmpty()) break;
            if (runnable.isCurrentInterrupt()) return;
            if (System.currentTimeMillis() - startTime >= timeout) break;
            runnable.sleep(retryInterval);
        }
        if (rect == null || rect.isEmpty()) {
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }
        int x;
        int y;
        if (random.getValue()) {
            x = rect.left + (int) (Math.random() * rect.width()) + offsetX;
            y = rect.top + (int) (Math.random() * rect.height()) + offsetY;
        } else {
            x = rect.centerX() + offsetX;
            y = rect.centerY() + offsetY;
        }

        // 按点击方式执行：单击 / 长按 / 双击 / 重复点击
        PinSingleSelect type = getPinValue(runnable, typePin);
        switch (type.getIndex()) {
            case 1 -> {
                // 长按
                int duration = 800;
                if (getPinValue(runnable, durationPin) instanceof PinInteger pinInteger) duration = Math.max(100, pinInteger.getValue());
                service.runGesture(x, y, duration, null);
            }
            case 2 -> {
                // 双击：两次快速点击
                service.runGesture(x, y, 50, null);
                runnable.sleep(getClickInterval(runnable));
                service.runGesture(x, y, 50, null);
            }
            case 3 -> {
                // 重复点击
                int repeat = 3;
                if (getPinValue(runnable, repeatPin) instanceof PinInteger pinInteger) repeat = Math.max(1, pinInteger.getValue());
                int interval = getClickInterval(runnable);
                for (int i = 0; i < repeat; i++) {
                    service.runGesture(x, y, 50, null);
                    if (i < repeat - 1) runnable.sleep(interval);
                }
            }
            default -> service.runGesture(x, y, 50, null);
        }
        TouchPathFloatView.showGesture(x, y);
        executeNext(runnable, outPin);
    }

    private int getClickInterval(TaskRunnable runnable) {
        int interval = 100;
        if (getPinValue(runnable, intervalPin) instanceof PinInteger pinInteger) interval = Math.max(50, pinInteger.getValue());
        return interval;
    }

    @Override
    public void check(ActionCheckResult result, Task task) {
        super.check(result, task);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            List<Action> actions = task.getActions(SwitchCaptureAction.class);
            if (actions.isEmpty()) {
                result.addResult(ActionCheckResult.ResultType.WARNING, R.string.check_need_capture_warning);
            }
        }
    }

    public Pin getTemplatePin() {
        return templatePin;
    }

    // 长按时长针脚：仅长按模式显示
    private static class DurationShowablePin extends ShowAblePin {
        public DurationShowablePin(PinBase value, int titleId, boolean out, boolean dynamic, boolean hide) {
            super(value, titleId, out, dynamic, hide);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof TouchImageAction action)) return false;
            PinSingleSelect type = action.typePin.getValue();
            return type != null && type.getIndex() == 1;
        }
    }

    // 重复次数针脚：仅重复点击模式显示
    private static class RepeatShowablePin extends ShowAblePin {
        public RepeatShowablePin(PinBase value, int titleId, boolean out, boolean dynamic, boolean hide) {
            super(value, titleId, out, dynamic, hide);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof TouchImageAction action)) return false;
            PinSingleSelect type = action.typePin.getValue();
            return type != null && type.getIndex() == 3;
        }
    }

    // 点击间隔针脚：双击和重复点击模式显示
    private static class IntervalShowablePin extends ShowAblePin {
        public IntervalShowablePin(PinBase value, int titleId, boolean out, boolean dynamic, boolean hide) {
            super(value, titleId, out, dynamic, hide);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof TouchImageAction action)) return false;
            PinSingleSelect type = action.typePin.getValue();
            return type != null && (type.getIndex() == 2 || type.getIndex() == 3);
        }
    }
}
