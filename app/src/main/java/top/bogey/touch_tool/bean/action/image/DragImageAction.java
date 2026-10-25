package top.bogey.touch_tool.bean.action.image;

import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.Set;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinValueArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.ui.custom.float_view.TouchPathFloatView;
import top.bogey.touch_tool.utils.DisplayUtil;

// 拖拽图片：匹配到模板位置后按住并拖动到目标坐标（滑块验证、拖拽排序）
public class DragImageAction extends ExecuteAction {
    private final transient Pin templatePin = new Pin(new PinImage(), R.string.touch_image_action_template);
    private final transient Pin delayPin = new Pin(new PinValueArea(0, 0), R.string.touch_image_action_delay, false, false, true);
    private final transient Pin similarityPin = new Pin(new PinInteger(80), R.string.touch_image_action_similarity);
    private final transient Pin areaPin = new Pin(new PinArea(), R.string.touch_image_action_area, false, false, false);
    private final transient Pin scalePin = new Pin(new PinSingleSelect(R.array.match_image_scale, 1), R.string.image_action_scale, false, false, true);
    private final transient Pin cannyPin = new Pin(new PinBoolean(false), R.string.image_action_canny, false, false, true);
    private final transient Pin targetXPin = new Pin(new PinInteger(500), R.string.drag_image_action_target_x);
    private final transient Pin targetYPin = new Pin(new PinInteger(500), R.string.drag_image_action_target_y);
    private final transient Pin targetAreaPin = new Pin(new PinArea(), R.string.drag_image_action_target_area, false, false, true);
    private final transient Pin durationPin = new NotLinkAblePin(new PinInteger(500), R.string.multi_finger_action_duration);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public DragImageAction() {
        super(ActionType.DRAG_IMAGE);
        addPins(templatePin, delayPin, similarityPin, areaPin, scalePin, cannyPin, targetXPin, targetYPin, targetAreaPin, durationPin, successPin, elsePin);
    }

    public DragImageAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(templatePin, delayPin, similarityPin, areaPin, scalePin, cannyPin, targetXPin, targetYPin, targetAreaPin, durationPin, successPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        // 拖拽前随机延时
        PinValueArea delay = getPinValue(runnable, delayPin);
        runnable.sleep(delay.getRandomValue());

        MainAccessibilityService service = MainApplication.getInstance().getService();
        successPin.getValue(PinBoolean.class).setValue(false);

        PinImage template = getPinValue(runnable, templatePin);
        PinNumber<?> similarity = getPinValue(runnable, similarityPin);
        PinArea area = getPinValue(runnable, areaPin);
        PinSingleSelect scale = getPinValue(runnable, scalePin);
        PinBoolean canny = getPinValue(runnable, cannyPin);
        int duration = 500;
        if (getPinValue(runnable, durationPin) instanceof PinInteger pinInteger) {
            duration = Math.max(100, pinInteger.getValue());
        }

        if (service == null) {
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        Bitmap bitmap = service.tryGetScreenShot();
        Rect rect = DisplayUtil.matchTemplate(bitmap, template.getImage(), area.getValue(), similarity.intValue(), scale.getIndex(), canny.getValue());
        if (rect == null || rect.isEmpty()) {
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        // 起点：匹配区域中心
        int startX = rect.centerX();
        int startY = rect.centerY();

        // 终点：目标区域针脚已连接时取其中心，否则用目标坐标
        int targetX;
        int targetY;
        if (targetAreaPin.isLinked()) {
            PinArea targetArea = getPinValue(runnable, targetAreaPin);
            Rect target = targetArea.getValue();
            if (target == null || target.isEmpty()) {
                markUnachieved();
                executeNext(runnable, elsePin);
                return;
            }
            targetX = target.centerX();
            targetY = target.centerY();
        } else {
            targetX = getInt(runnable, targetXPin, 500);
            targetY = getInt(runnable, targetYPin, 500);
        }

        // 线性拖拽：按住匹配位置滑动到目标位置
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(targetX, targetY);
        Set<GestureDescription.StrokeDescription> strokes = Collections.singleton(new GestureDescription.StrokeDescription(path, 0, duration));
        service.runGesture(strokes, result -> { });
        TouchPathFloatView.showGesture(startX, startY);
        successPin.getValue(PinBoolean.class).setValue(true);
        executeNext(runnable, outPin);
    }

    private int getInt(TaskRunnable runnable, Pin conditionPin, int def) {
        if (getPinValue(runnable, conditionPin) instanceof PinInteger pinInteger) return pinInteger.getValue();
        return def;
    }
}
