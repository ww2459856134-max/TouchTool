package top.bogey.touch_tool.bean.action.node;

import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDouble;
import top.bogey.touch_tool.service.TaskRunnable;

// 进度条设置：设置 SeekBar 等控件的进度值（百分比）
public class NodeSetProgressAction extends ExecuteAction {
    private final transient Pin nodePin = new Pin(new PinNode(), R.string.pin_node);
    private final transient Pin progressPin = new Pin(new PinDouble(50), R.string.node_set_progress_action_progress);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public NodeSetProgressAction() {
        super(ActionType.NODE_SET_PROGRESS);
        addPins(nodePin, progressPin, resultPin);
    }

    public NodeSetProgressAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, progressPin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinNode node = getPinValue(runnable, nodePin);
        resultPin.getValue(PinBoolean.class).setValue(false);

        if (node != null && node.getNodeInfo() != null && node.getNodeInfo().node != null) {
            double progress = 50;
            if (getPinValue(runnable, progressPin) instanceof PinDouble pinDouble) {
                progress = pinDouble.getValue();
            }
            progress = Math.max(0, Math.min(100, progress));

            Bundle args = new Bundle();
            args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, (float) progress);
            resultPin.getValue(PinBoolean.class).setValue(
                    node.getNodeInfo().node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(), args));
        }
        executeNext(runnable, outPin);
    }
}
