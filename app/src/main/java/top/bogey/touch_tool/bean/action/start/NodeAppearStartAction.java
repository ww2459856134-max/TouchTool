package top.bogey.touch_tool.bean.action.start;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleLineString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.service.TaskRunnable;

// 应用内控件出现触发器：指定应用的界面上出现包含指定文本的控件时执行
public class NodeAppearStartAction extends StartAction {
    private final transient Pin packagePin = new NotLinkAblePin(new PinSingleLineString(), R.string.node_appear_start_action_package);
    private final transient Pin textPin = new NotLinkAblePin(new PinString(), R.string.node_appear_start_action_text);
    private final transient Pin foundTextPin = new Pin(new PinString(), R.string.node_appear_start_action_found_text, true);
    private final transient Pin foundPackagePin = new Pin(new PinString(), R.string.node_appear_start_action_found_package, true);

    public NodeAppearStartAction() {
        super(ActionType.NODE_APPEAR_START);
        addPins(packagePin, textPin, foundTextPin, foundPackagePin);
    }

    public NodeAppearStartAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(packagePin, textPin, foundTextPin, foundPackagePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        TaskInfoSummary.NodeAppearInfo info = TaskInfoSummary.getInstance().getNodeAppearInfo();
        if (info != null) {
            foundTextPin.setValue(new PinString(info.text()));
            foundPackagePin.setValue(new PinString(info.packageName()));
        }
        executeNext(runnable, executePin);
    }

    @Override
    public boolean ready() {
        TaskInfoSummary.NodeAppearInfo info = TaskInfoSummary.getInstance().getNodeAppearInfo();
        if (info == null) return false;

        String text = getText();
        if (text == null || text.isEmpty()) return false;
        // 匹配的是控件文本中包含关键字
        if (!info.text().contains(text)) return false;

        String packageName = getPackageName();
        // 包名为空时不限定应用
        if (packageName != null && !packageName.isEmpty() && !packageName.equals(info.packageName())) return false;
        return true;
    }

    public String getPackageName() {
        return packagePin.getValue(PinSingleLineString.class).getValue();
    }

    public String getText() {
        return textPin.getValue(PinString.class).getValue();
    }
}
