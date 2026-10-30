package top.bogey.touch_tool.bean.action.image;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionCheckResult;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.action.system.SwitchCaptureAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinColor;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.special_pin.NotShowPin;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.ui.custom.float_view.TouchPathFloatView;
import top.bogey.touch_tool.utils.DisplayUtil;
import top.bogey.touch_tool.utils.HidClient;

public class TouchColorAction extends ExecuteAction {
    private final transient Pin templatePin = new Pin(new PinColor(), R.string.touch_color_action_template);
    private final transient Pin similarityPin = new Pin(new PinInteger(80), R.string.touch_color_action_similarity);
    private final transient Pin areaPin = new Pin(new PinArea(), R.string.touch_color_action_area, false, false, true);
    private final transient Pin useAccPin = new NotShowPin(new PinBoolean(true), R.string.touch_color_action_use_accessibility, false, false, true);
    private final transient Pin offsetPin = new Pin(new PinBoolean(), R.string.touch_color_action_offset);
    private final transient Pin touchAllPin = new NotLinkAblePin(new PinBoolean(false), R.string.touch_color_action_touch_all);
    private final transient Pin touchIntervalPin = new TouchIntervalShowablePin(new PinInteger(300), R.string.touch_color_action_touch_interval);
    // 执行通道：无障碍 / HID 触摸（ESP32，取设置页全局配置），失败自动回退无障碍
    private final transient Pin channelPin = new NotLinkAblePin(new PinSingleSelect(R.array.touch_image_channel), R.string.touch_image_channel);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public TouchColorAction() {
        super(ActionType.TOUCH_COLOR);
        addPins(templatePin, similarityPin, areaPin, useAccPin, offsetPin, touchAllPin, touchIntervalPin, channelPin, elsePin);
    }

    public TouchColorAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(templatePin, similarityPin, areaPin, useAccPin, offsetPin, touchAllPin, touchIntervalPin, channelPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        MainAccessibilityService service = MainApplication.getInstance().getService();
        Bitmap bitmap = service.tryGetScreenShot();

        PinColor template = getPinValue(runnable, templatePin);
        PinNumber<?> similarity = getPinValue(runnable, similarityPin);
        PinArea area = getPinValue(runnable, areaPin);

        List<Rect> rectList = DisplayUtil.matchColor(bitmap, template.getValue().getColor(), area.getValue(), similarity.intValue());
        if (rectList != null && !rectList.isEmpty()) {
            List<Rect> validList = new ArrayList<>();
            int minArea = template.getValue().getMinArea();
            int maxArea = template.getValue().getMaxArea();
            for (Rect rect : rectList) {
                int size = rect.width() * rect.height();
                if (size >= minArea && size <= maxArea) {
                    validList.add(rect);
                }
            }
            if (validList.isEmpty()) {
                markUnachieved();
                executeNext(runnable, elsePin);
                return;
            }

            if (isTouchAll()) {
                PinNumber<?> interval = getPinValue(runnable, touchIntervalPin);
                int index = 0;
                while (index < validList.size()) {
                    Rect rect = validList.get(index);
                    touch(runnable, rect);
                    runnable.sleep(interval.intValue());
                    index++;
                }
            } else {
                touch(runnable, validList.get(0));
            }
            executeNext(runnable, outPin);
            return;
        }
        markUnachieved();
        executeNext(runnable, elsePin);
    }

    private void touch(TaskRunnable runnable, Rect rect) {
        PinBoolean random = getPinValue(runnable, offsetPin);
        int x;
        int y;
        if (random.getValue()) {
            x = rect.left + (int) (Math.random() * rect.width());
            y = rect.top + (int) (Math.random() * rect.height());
        } else {
            x = rect.centerX();
            y = rect.centerY();
        }

        // HID 通道：通过 ESP32 蓝牙 HID 点击，无需无障碍注入；失败回退无障碍
        PinSingleSelect channel = getPinValue(runnable, channelPin);
        if (channel != null && channel.getIndex() == 1) {
            String host = HidClient.getConfigHost();
            int port = HidClient.getConfigPort();
            if (host != null) {
                android.graphics.Point p = new android.graphics.Point(x, y);
                StringBuilder hexLog = new StringBuilder();
                boolean ok = HidClient.sendGesture(host, port, java.util.Arrays.asList(p, p), 60, hexLog);
                if (ok) {
                    runnable.sleep(90);
                    TouchPathFloatView.showGesture(x, y);
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
        service.runGesture(x, y, 50, null);
        TouchPathFloatView.showGesture(x, y);
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

    private boolean isTouchAll() {
        return touchAllPin.getValue(PinBoolean.class).getValue();
    }


    private static class TouchIntervalShowablePin extends ShowAblePin {
        public TouchIntervalShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            TouchColorAction action = (TouchColorAction) context.getAction(getOwnerId());
            return action.isTouchAll();
        }
    }
}
