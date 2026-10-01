package top.bogey.touch_tool.bean.action.node;

import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonObject;

import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.service.TaskRunnable;

// 遍历点击：按顺序点击列表中的所有控件
public class NodeTouchAllAction extends ExecuteAction {
    private final transient Pin nodesPin = new Pin(new PinList(new PinNode()), R.string.node_touch_all_action_nodes);
    private final transient Pin intervalPin = new Pin(new PinInteger(500), R.string.node_touch_all_action_interval);
    private final transient Pin countPin = new Pin(new PinInteger(0), R.string.node_touch_all_action_count, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.node_touch_action_else, true);

    public NodeTouchAllAction() {
        super(ActionType.NODE_TOUCH_ALL);
        addPins(nodesPin, intervalPin, countPin, elsePin);
    }

    public NodeTouchAllAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodesPin, intervalPin, countPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinList nodes = getPinValue(runnable, nodesPin);
        long interval = 500;
        if (getPinValue(runnable, intervalPin) instanceof PinInteger pinInteger) {
            interval = Math.max(0, pinInteger.getValue());
        }

        int count = 0;
        if (nodes != null) {
            for (PinObject item : nodes) {
                if (!(item instanceof PinNode node) || node.getNodeInfo() == null || node.getNodeInfo().node == null) {
                    continue;
                }
                if (node.getNodeInfo().node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    count++;
                    if (interval > 0) runnable.sleep(interval);
                }
            }
        }

        countPin.getValue(PinInteger.class).setValue(count);
        executeNext(runnable, count > 0 ? outPin : elsePin);
    }
}
