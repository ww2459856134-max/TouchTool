package top.bogey.touch_tool.bean.action.node;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.service.TaskRunnable;

public class GetWindowsAction extends CalculateAction {
    private final transient Pin windowsPin = new Pin(new PinList(new PinNode()), true);

    public GetWindowsAction() {
        super(ActionType.GET_WINDOWS);
        addPin(windowsPin);
    }

    public GetWindowsAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(windowsPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        for (NodeInfo window : NodeInfo.getWindows()) {
            windowsPin.getValue(PinList.class).add(new PinNode(window));
        }
    }
}
