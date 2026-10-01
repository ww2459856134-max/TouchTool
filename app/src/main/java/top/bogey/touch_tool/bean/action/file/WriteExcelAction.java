package top.bogey.touch_tool.bean.action.file;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.ExcelUtil;

// 写入 Excel：写指定单元格或按分隔符追加一行，文件不存在自动创建
public class WriteExcelAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinString(), R.string.excel_action_path);
    private final transient Pin modePin = new NotLinkAblePin(new PinSingleSelect(R.array.excel_write_mode), R.string.excel_action_mode);
    private final transient Pin rowPin = new RowShowablePin(new PinInteger(1), R.string.excel_action_row);
    private final transient Pin colPin = new ColShowablePin(new PinInteger(1), R.string.excel_action_col);
    private final transient Pin valuePin = new Pin(new PinString(), R.string.excel_action_value);
    private final transient Pin sheetPin = new NotLinkAblePin(new PinInteger(1), R.string.excel_action_sheet, false, false, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public WriteExcelAction() {
        super(ActionType.EXCEL_WRITE);
        addPins(pathPin, modePin, rowPin, colPin, valuePin, sheetPin, successPin, elsePin);
    }

    public WriteExcelAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, modePin, rowPin, colPin, valuePin, sheetPin, successPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        int mode = 0;
        if (getPinValue(runnable, modePin) instanceof PinSingleSelect select) mode = select.getIndex();
        int row = 1;
        if (getPinValue(runnable, rowPin) instanceof PinInteger pinInteger) row = Math.max(1, pinInteger.getValue());
        int col = 1;
        if (getPinValue(runnable, colPin) instanceof PinInteger pinInteger) col = Math.max(1, pinInteger.getValue());
        String value = getPinValue(runnable, valuePin).toString();
        int sheet = 1;
        if (getPinValue(runnable, sheetPin) instanceof PinInteger pinInteger) sheet = Math.max(1, pinInteger.getValue());

        successPin.getValue(PinBoolean.class).setValue(false);

        try {
            if (mode == 0) {
                ExcelUtil.writeCell(path, sheet, row, col, value);
            } else {
                ExcelUtil.appendRow(path, sheet, value, ",");
            }
            successPin.getValue(PinBoolean.class).setValue(true);
            executeNext(runnable, outPin);
        } catch (Exception e) {
            executeNext(runnable, elsePin);
        }
    }

    // 行号针脚：仅写单元格模式显示
    private static class RowShowablePin extends NotLinkAblePin {
        public RowShowablePin(PinBase value, int titleId) {
            super(value, titleId, false, false, true);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof WriteExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            return mode != null && mode.getIndex() == 0;
        }
    }

    // 列号针脚：仅写单元格模式显示
    private static class ColShowablePin extends NotLinkAblePin {
        public ColShowablePin(PinBase value, int titleId) {
            super(value, titleId, false, false, true);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof WriteExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            return mode != null && mode.getIndex() == 0;
        }
    }
}
