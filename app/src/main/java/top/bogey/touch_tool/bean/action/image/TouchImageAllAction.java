package top.bogey.touch_tool.bean.action.image;

import android.graphics.Bitmap;
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
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.PinValueArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.ui.custom.float_view.TouchPathFloatView;
import top.bogey.touch_tool.utils.DisplayUtil;
import top.bogey.touch_tool.utils.MatchResult;

// 多图点击：对多张不同的模板图依次查找，每张点击匹配到的位置
public class TouchImageAllAction extends ExecuteAction {
    private final transient Pin templatesPin = new Pin(new PinList(new PinImage()), R.string.touch_image_all_action_templates);
    private final transient Pin delayPin = new Pin(new PinValueArea(0, 0), R.string.touch_image_action_delay, false, false, true);
    private final transient Pin similarityPin = new Pin(new PinInteger(80), R.string.touch_image_action_similarity);
    private final transient Pin areaPin = new Pin(new PinArea(), R.string.touch_image_action_area, false, false, false);
    private final transient Pin scalePin = new Pin(new PinSingleSelect(R.array.match_image_scale, 1), R.string.image_action_scale, false, false, true);
    private final transient Pin cannyPin = new Pin(new PinBoolean(false), R.string.image_action_canny, false, false, true);
    private final transient Pin randomPin = new Pin(new PinBoolean(), R.string.touch_image_action_offset);
    private final transient Pin intervalPin = new Pin(new PinInteger(300), R.string.touch_image_action_click_interval);
    private final transient Pin countPin = new Pin(new PinInteger(0), R.string.touch_image_all_action_count, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public TouchImageAllAction() {
        super(ActionType.TOUCH_IMAGE_ALL);
        addPins(templatesPin, delayPin, similarityPin, areaPin, scalePin, cannyPin, randomPin, intervalPin, countPin, elsePin);
    }

    public TouchImageAllAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(templatesPin, delayPin, similarityPin, areaPin, scalePin, cannyPin, randomPin, intervalPin, countPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        // 点击前随机延时（最小到最大之间）
        PinValueArea delay = getPinValue(runnable, delayPin);
        runnable.sleep(delay.getRandomValue());

        MainAccessibilityService service = MainApplication.getInstance().getService();
        Bitmap bitmap = service.tryGetScreenShot();

        PinList templates = getPinValue(runnable, templatesPin);
        PinNumber<?> similarity = getPinValue(runnable, similarityPin);
        PinArea area = getPinValue(runnable, areaPin);
        PinSingleSelect scale = getPinValue(runnable, scalePin);
        PinBoolean canny = getPinValue(runnable, cannyPin);
        PinBoolean random = getPinValue(runnable, randomPin);
        long interval = 300;
        if (getPinValue(runnable, intervalPin) instanceof PinInteger pinInteger) interval = Math.max(0, pinInteger.getValue());

        countPin.getValue(PinInteger.class).setValue(0);

        int count = 0;
        if (templates != null && bitmap != null) {
            // 每张模板图点击前重新截图，保证点击的是当前界面状态
            for (PinObject item : templates) {
                if (runnable.isCurrentInterrupt()) return;
                if (!(item instanceof PinImage template) || template.getImage() == null) continue;

                List<MatchResult> results = DisplayUtil.matchAllTemplate(bitmap, template.getImage(), area.getValue(), similarity.intValue(), scale.getIndex(), canny.getValue());
                if (results == null || results.isEmpty() || results.get(0).area == null || results.get(0).area.isEmpty()) continue;

                android.graphics.Rect rect = results.get(0).area;
                int x;
                int y;
                if (random.getValue()) {
                    x = rect.left + (int) (Math.random() * rect.width());
                    y = rect.top + (int) (Math.random() * rect.height());
                } else {
                    x = rect.centerX();
                    y = rect.centerY();
                }
                service.runGesture(x, y, 50, null);
                TouchPathFloatView.showGesture(x, y);
                count++;

                if (interval > 0) runnable.sleep(interval);
                // 截一张新图再找下一张模板
                bitmap = service.tryGetScreenShot();
            }
        }

        countPin.getValue(PinInteger.class).setValue(count);
        executeNext(runnable, count > 0 ? outPin : elsePin);
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
}
