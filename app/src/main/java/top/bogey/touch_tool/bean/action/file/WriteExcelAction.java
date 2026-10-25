package top.bogey.touch_tool.bean.action.file;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinFilePathString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.ExcelUtil;

// 写入 Excel/CSV：写指定单元格或按分隔符追加一行，文件不存在自动创建
// xlsx 写入保留原文件的其他工作表与样式；数字内容可按数字类型写入（Excel 中可直接计算）
// 文件路径以 .csv 结尾时自动按 CSV 处理
public class WriteExcelAction extends ExecuteAction {
    // 模式索引（对应 excel_write_mode 数组顺序）
    public static final int MODE_CELL = 0;
    public static final int MODE_APPEND = 1;
    public static final int MODE_DELETE_ROW = 2;
    public static final int MODE_DELETE_COL = 3;

    private final transient Pin pathPin = new Pin(new PinFilePathString(), R.string.excel_action_path);
    private final transient Pin modePin = new NotLinkAblePin(new PinSingleSelect(R.array.excel_write_mode), R.string.excel_action_mode);
    private final transient Pin rowPin = new RowShowablePin(new PinInteger(1), R.string.excel_action_row);
    private final transient Pin colPin = new ColShowablePin(new PinInteger(1), R.string.excel_action_col);
    private final transient Pin valuePin = new Pin(new PinString(), R.string.excel_action_value);
    private final transient Pin sheetPin = new NotLinkAblePin(new PinInteger(1), R.string.excel_action_sheet, false, false, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);
    // 以下新针脚追加在末尾，旧任务按位置同步不受影响
    // 列分隔符：追加一行拆分单元格、CSV 列分隔用；留空时 xlsx 默认逗号、CSV 默认逗号
    private final transient Pin colSepPin = new NotLinkAblePin(new PinString(), R.string.excel_action_col_sep);
    // 按数字写入：勾选且内容是数字时，单元格不带文本标记（仅写单元格模式显示）
    private final transient Pin numericPin = new NumericShowablePin(new PinBoolean(false), R.string.excel_action_numeric);
    // 失败原因
    private final transient Pin errorPin = new Pin(new PinString(), R.string.excel_action_error, true);
    // A1 记法：如 B3，填了就优先于行号/列号（仅写单元格模式显示）
    private final transient Pin a1Pin = new A1ShowablePin(new PinString(), R.string.excel_action_a1);
    // 工作表名称：填了就优先于工作表序号
    private final transient Pin sheetNamePin = new NotLinkAblePin(new PinString(), R.string.excel_action_sheet_name);
    // 按公式写入：勾选且内容以 = 开头时写入为 Excel 公式（仅写单元格模式显示）
    private final transient Pin formulaPin = new FormulaShowablePin(new PinBoolean(false), R.string.excel_action_formula);

    public WriteExcelAction() {
        super(ActionType.EXCEL_WRITE);
        addPins(pathPin, modePin, rowPin, colPin, valuePin, sheetPin, successPin, elsePin, colSepPin, numericPin, errorPin, a1Pin, sheetNamePin, formulaPin);
    }

    public WriteExcelAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, modePin, rowPin, colPin, valuePin, sheetPin, successPin, elsePin, colSepPin, numericPin, errorPin, a1Pin, sheetNamePin, formulaPin);
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
        String colSep = getPinValue(runnable, colSepPin).toString();
        boolean numeric = getPinValue(runnable, numericPin) instanceof PinBoolean pinBoolean && pinBoolean.getValue();
        String a1 = getPinValue(runnable, a1Pin).toString().trim();
        String sheetName = getPinValue(runnable, sheetNamePin).toString().trim();
        boolean formula = getPinValue(runnable, formulaPin) instanceof PinBoolean pinBoolean2 && pinBoolean2.getValue();

        successPin.getValue(PinBoolean.class).setValue(false);
        errorPin.setValue(new PinString(""));

        // A1 记法优先于行号/列号
        if (!a1.isEmpty()) {
            int[] rc = ExcelUtil.parseA1(a1);
            if (rc != null) {
                row = rc[0];
                col = rc[1];
            } else {
                errorPin.setValue(new PinString("A1 记法格式不对，应像 B3 这样"));
                markUnachieved();
                executeNext(runnable, elsePin);
                return;
            }
        }

        boolean csv = path.trim().toLowerCase().endsWith(".csv");
        try {
            if (csv) {
                String sep = colSep.isEmpty() ? "," : colSep;
                List<List<String>> grid = ExcelUtil.readCsvGrid(path, sep);
                switch (mode) {
                    case MODE_CELL:
                        if (formula) {
                            errorPin.setValue(new PinString("CSV 不支持公式，请用 xlsx 文件"));
                            markUnachieved();
                            executeNext(runnable, elsePin);
                            return;
                        }
                        while (grid.size() < row) grid.add(new ArrayList<>());
                        List<String> line = grid.get(row - 1);
                        while (line.size() < col) line.add("");
                        line.set(col - 1, value);
                        break;
                    case MODE_APPEND: {
                        List<String> line1 = new ArrayList<>();
                        for (String item : value.split(java.util.regex.Pattern.quote(sep), -1)) line1.add(item);
                        grid.add(line1);
                        break;
                    }
                    case MODE_DELETE_ROW:
                        if (row < 1 || row > grid.size()) {
                            errorPin.setValue(new PinString("行号超出范围（共 " + grid.size() + " 行）"));
                            markUnachieved();
                            executeNext(runnable, elsePin);
                            return;
                        }
                        grid.remove(row - 1);
                        break;
                    case MODE_DELETE_COL:
                        for (List<String> line2 : grid) {
                            if (col <= line2.size()) line2.remove(col - 1);
                        }
                        break;
                }
                ExcelUtil.writeCsvGrid(path, grid, sep);
            } else {
                // 表名优先于序号
                int sheetIndex = sheet - 1;
                if (!sheetName.isEmpty()) {
                    List<String> names = ExcelUtil.readSheetNames(path);
                    int idx = names.indexOf(sheetName);
                    if (idx < 0) {
                        errorPin.setValue(new PinString("找不到工作表：" + sheetName));
                        markUnachieved();
                        executeNext(runnable, elsePin);
                        return;
                    }
                    sheetIndex = idx;
                }
                switch (mode) {
                    case MODE_CELL:
                        if (formula) {
                            if (!ExcelUtil.surgicalWriteFormula(path, sheetIndex, row, col, value)) {
                                errorPin.setValue(new PinString("表结构较复杂，无法直接写公式"));
                                markUnachieved();
                                executeNext(runnable, elsePin);
                                return;
                            }
                        } else {
                            ExcelUtil.writeCell(path, sheetIndex, row, col, value, numeric);
                        }
                        break;
                    case MODE_APPEND: {
                        String sep = colSep.isEmpty() ? "," : colSep;
                        ExcelUtil.appendRow(path, sheetIndex, value, sep);
                        break;
                    }
                    case MODE_DELETE_ROW:
                        if (!ExcelUtil.surgicalDeleteRow(path, sheetIndex, row)) {
                            errorPin.setValue(new PinString("删除行失败，行可能不存在或表结构较复杂"));
                            markUnachieved();
                            executeNext(runnable, elsePin);
                            return;
                        }
                        break;
                    case MODE_DELETE_COL:
                        if (!ExcelUtil.surgicalDeleteColumn(path, sheetIndex, col)) {
                            errorPin.setValue(new PinString("删除列失败，列可能不存在或表结构较复杂"));
                            markUnachieved();
                            executeNext(runnable, elsePin);
                            return;
                        }
                        break;
                }
            }
            successPin.getValue(PinBoolean.class).setValue(true);
            executeNext(runnable, outPin);
        } catch (Exception e) {
            errorPin.setValue(new PinString(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            markUnachieved();
            executeNext(runnable, elsePin);
        }
    }

    // 行号针脚：写单元格 / 删除行模式显示
    private static class RowShowablePin extends NotLinkAblePin {
        public RowShowablePin(PinBase value, int titleId) {
            super(value, titleId, false, false, true);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof WriteExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            return mode != null && (mode.getIndex() == MODE_CELL || mode.getIndex() == MODE_DELETE_ROW);
        }
    }

    // 列号针脚：写单元格 / 删除列模式显示
    private static class ColShowablePin extends NotLinkAblePin {
        public ColShowablePin(PinBase value, int titleId) {
            super(value, titleId, false, false, true);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof WriteExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            return mode != null && (mode.getIndex() == MODE_CELL || mode.getIndex() == MODE_DELETE_COL);
        }
    }

    // A1 记法：仅写单元格模式显示
    private static class A1ShowablePin extends NotLinkAblePin {
        public A1ShowablePin(PinBase value, int titleId) {
            super(value, titleId, false, false, true);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof WriteExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            return mode != null && mode.getIndex() == MODE_CELL;
        }
    }

    // 数字写入开关：仅写单元格模式显示
    private static class NumericShowablePin extends ShowAblePin {
        public NumericShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof WriteExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            return mode != null && mode.getIndex() == MODE_CELL;
        }
    }

    // 公式写入开关：仅写单元格模式显示
    private static class FormulaShowablePin extends ShowAblePin {
        public FormulaShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof WriteExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            return mode != null && mode.getIndex() == MODE_CELL;
        }
    }
}
