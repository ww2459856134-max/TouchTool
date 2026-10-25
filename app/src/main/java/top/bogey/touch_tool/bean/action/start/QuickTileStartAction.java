package top.bogey.touch_tool.bean.action.start;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleLineString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

// 快捷磁贴触发器：点击下拉通知栏中绑定的任务磁贴时执行
public class QuickTileStartAction extends StartAction {
    private final transient Pin slotPin = new NotLinkAblePin(new PinSingleSelect(R.array.tile_slot), R.string.quick_tile_start_action_slot);
    private final transient Pin namePin = new NotLinkAblePin(new PinSingleLineString(), R.string.quick_tile_start_action_name);

    public QuickTileStartAction() {
        super(ActionType.QUICK_TILE_START);
        addPins(slotPin, namePin);
    }

    public QuickTileStartAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(slotPin, namePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        executeNext(runnable, executePin);
    }

    public String getName() {
        return namePin.getValue(PinSingleLineString.class).getValue();
    }

    // 获取绑定的磁贴编号（1-4）
    public int getSlot() {
        return slotPin.getValue(PinSingleSelect.class).getIndex() + 1;
    }
}
