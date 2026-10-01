package top.bogey.touch_tool.bean.action.node;

import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;

// 输入框输入：向输入框输入文本或粘贴剪贴板内容（原输入框输入文本与输入框粘贴合并）
public class EditTextInputAction extends ExecuteAction {
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.edit_text_input_action_edit_text, false, false, true);
    private final transient Pin indexPin = new NotLinkAblePin(new PinInteger(0), R.string.edit_text_input_action_index);
    private final transient Pin modePin = new NotLinkAblePin(new PinSingleSelect(R.array.edit_text_input_mode), R.string.edit_text_input_action_mode);
    private final transient Pin contentPin = new ContentShowablePin(new PinString(), R.string.pin_string, false, false, true);
    private final transient Pin appendPin = new ContentShowablePin(new PinBoolean(), R.string.edit_text_input_action_append, false, false, true);
    private final transient Pin enterPin = new EnterShowablePin(new PinBoolean(), R.string.edit_text_input_action_enter, false, false, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.node_touch_action_else, true);

    public EditTextInputAction() {
        this(ActionType.EDITTEXT_INPUT);
    }

    public EditTextInputAction(ActionType type) {
        super(type);
        addPins(nodePin, indexPin, modePin, contentPin, appendPin, enterPin, elsePin);
        // 兼容旧的输入框粘贴动作，默认为粘贴模式
        if (type == ActionType.EDITTEXT_PASTE) modePin.getValue(PinSingleSelect.class).setIndex(1);
    }

    public EditTextInputAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, indexPin, modePin, contentPin, appendPin, enterPin, elsePin);
        // 兼容旧的输入框粘贴动作，默认为粘贴模式
        if (getType() == ActionType.EDITTEXT_PASTE) modePin.getValue(PinSingleSelect.class).setIndex(1);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        NodeInfo nodeInfo = null;
        if (nodePin.isLinked()) {
            PinNode node = getPinValue(runnable, nodePin);
            nodeInfo = node.getNodeInfo();
        } else {
            // 未连接输入框针脚时按序号自动查找输入框
            nodeInfo = findEditTextByIndex(getPinValue(runnable, indexPin));
        }

        PinSingleSelect mode = getPinValue(runnable, modePin);
        PinBoolean enter = getPinValue(runnable, enterPin);

        boolean result = false;
        if (nodeInfo != null && nodeInfo.usable && nodeInfo.node != null && nodeInfo.node.isFocusable()) {
            nodeInfo.node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

            if (mode.getIndex() == 1) {
                // 粘贴剪贴板内容
                result = nodeInfo.node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            } else {
                // 直接输入文本
                PinObject content = getPinValue(runnable, contentPin);
                PinBoolean append = getPinValue(runnable, appendPin);

                String contentValue = content.toString();
                if (append.getValue() && nodeInfo.text != null) {
                    contentValue = nodeInfo.text + contentValue;
                }

                Bundle bundle = new Bundle();
                bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, contentValue);
                result = nodeInfo.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);
            }

            if (result && enter.getValue() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                nodeInfo.node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
            }
        }
        executeNext(runnable, result ? outPin : elsePin);
    }

    // 按序号查找输入框：0 为第一个，-1 为最后一个，越界回退到第一个
    private NodeInfo findEditTextByIndex(PinInteger indexValue) {
        int index = 0;
        if (indexValue instanceof PinInteger pinInteger) index = pinInteger.getValue();

        List<NodeInfo> list = new ArrayList<>();
        for (NodeInfo window : NodeInfo.getWindows()) {
            collectEditTexts(window, list);
        }
        if (list.isEmpty()) return null;

        if (index < 0) index = list.size() + index;
        if (index < 0 || index >= list.size()) index = 0;
        return list.get(index);
    }

    // 递归收集界面上所有可见的输入框
    private void collectEditTexts(NodeInfo parent, List<NodeInfo> list) {
        if (parent == null) return;
        if (EditText.class.getName().equals(parent.clazz) && parent.visible) list.add(parent);
        for (NodeInfo child : parent.getChildren()) {
            collectEditTexts(child, list);
        }
    }

    // 内容与追加针脚仅在文本输入模式下显示
    private static class ContentShowablePin extends ShowAblePin {
        public ContentShowablePin(PinBase value, int titleId, boolean out, boolean dynamic, boolean hide) {
            super(value, titleId, out, dynamic, hide);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof EditTextInputAction action)) return false;
            PinSingleSelect select = action.modePin.getValue();
            return select == null || select.getIndex() == 0;
        }
    }

    private static class EnterShowablePin extends ShowAblePin {
        public EnterShowablePin(PinBase value, int titleId, boolean out, boolean dynamic, boolean hide) {
            super(value, titleId, out, dynamic, hide);
        }

        @Override
        public boolean showAble(Task context) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        }
    }
}
