package top.bogey.touch_tool.bean.action.file;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinFilePathString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.ExcelUtil;

// 读取 Excel/CSV：读单元格 / 读全表 / 读一行 / 读一列 / 按表头取值 / 列出工作表
// 文件路径以 .csv 结尾时自动按 CSV 处理（编码自动：UTF-8 优先、GBK 回退）
public class ReadExcelAction extends ExecuteAction {
    // 模式索引（对应 excel_read_mode 数组顺序）
    public static final int MODE_CELL = 0;
    public static final int MODE_ALL = 1;
    public static final int MODE_ROW = 2;
    public static final int MODE_COL = 3;
    public static final int MODE_BY_HEADER = 4;
    public static final int MODE_SHEETS = 5;

    private final transient Pin pathPin = new Pin(new PinFilePathString(), R.string.excel_action_path);
    private final transient Pin modePin = new NotLinkAblePin(new PinSingleSelect(R.array.excel_read_mode), R.string.excel_action_mode);
    private final transient Pin rowPin = new RowShowablePin(new PinInteger(1), R.string.excel_action_row);
    private final transient Pin colPin = new ColShowablePin(new PinInteger(1), R.string.excel_action_col);
    private final transient Pin sheetPin = new NotLinkAblePin(new PinInteger(1), R.string.excel_action_sheet, false, false, true);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.pin_string, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);
    // 以下新针脚追加在末尾，旧任务按位置同步不受影响
    // 列分隔符：读全表用；留空时 xlsx 默认制表符、CSV 默认逗号
    private final transient Pin colSepPin = new NotLinkAblePin(new PinString(), R.string.excel_action_col_sep);
    // 输出列表：读一行/读一列/列出工作表时使用
    private final transient Pin listPin = new Pin(new PinList(new PinString()), R.string.excel_action_list, true);
    // 失败原因
    private final transient Pin errorPin = new Pin(new PinString(), R.string.excel_action_error, true);
    // 列名：按表头取值模式使用（首行为表头）
    private final transient Pin namePin = new NameShowablePin(new PinString(), R.string.excel_action_col_name);
    // A1 引用：填了优先于行号/列号，如 B3
    private final transient Pin a1Pin = new RowColShowablePin(new PinString(), R.string.excel_action_a1);
    // 工作表名：填了优先于工作表序号
    private final transient Pin sheetNamePin = new NotLinkAblePin(new PinString(), R.string.excel_action_sheet_name);
    // 表的规模：总行数 / 总列数
    private final transient Pin maxRowPin = new Pin(new PinInteger(0), R.string.excel_action_max_row, true);
    private final transient Pin maxColPin = new Pin(new PinInteger(0), R.string.excel_action_max_col, true);

    public ReadExcelAction() {
        super(ActionType.EXCEL_READ);
        addPins(pathPin, modePin, rowPin, colPin, sheetPin, resultPin, successPin, elsePin, colSepPin, listPin, errorPin, namePin,
                a1Pin, sheetNamePin, maxRowPin, maxColPin);
    }

    public ReadExcelAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, modePin, rowPin, colPin, sheetPin, resultPin, successPin, elsePin, colSepPin, listPin, errorPin, namePin,
                a1Pin, sheetNamePin, maxRowPin, maxColPin);
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
        int sheet = 1;
        if (getPinValue(runnable, sheetPin) instanceof PinInteger pinInteger) sheet = Math.max(1, pinInteger.getValue());
        String colSep = getPinValue(runnable, colSepPin).toString();
        String name = getPinValue(runnable, namePin).toString();
        String a1 = getPinValue(runnable, a1Pin).toString().trim();
        String sheetName = getPinValue(runnable, sheetNamePin).toString().trim();
        maxRowPin.getValue(PinInteger.class).setValue(0);
        maxColPin.getValue(PinInteger.class).setValue(0);

        // A1 引用填了优先于行列号
        if (!a1.isEmpty() && a1.matches("(?i)[A-Z]+\\d+")) {
            int[] pos = ExcelUtil.parseA1(a1);
            row = Math.max(1, pos[0]);
            col = Math.max(1, pos[1]);
        }

        successPin.getValue(PinBoolean.class).setValue(false);
        resultPin.setValue(new PinString(""));
        listPin.getValue(PinList.class).clear();
        errorPin.setValue(new PinString(""));

        boolean csv = path.trim().toLowerCase().endsWith(".csv");
        try {
            String result = "";
            List<String> listResult = null;
            if (csv) {
                String sep = colSep.isEmpty() ? "," : colSep;
                List<List<String>> grid = ExcelUtil.readCsvGrid(path, sep);
                maxRowPin.getValue(PinInteger.class).setValue(grid.size());
                int maxLen = 0;
                for (List<String> cells : grid) maxLen = Math.max(maxLen, cells.size());
                maxColPin.getValue(PinInteger.class).setValue(maxLen);
                switch (mode) {
                    case MODE_ALL -> {
                        StringBuilder builder = new StringBuilder();
                        for (List<String> cells : grid) {
                            if (builder.length() > 0) builder.append('\n');
                            for (int i = 0; i < cells.size(); i++) {
                                if (i > 0) builder.append(sep);
                                builder.append(cells.get(i));
                            }
                        }
                        result = builder.toString();
                    }
                    case MODE_ROW -> {
                        if (row - 1 < grid.size()) listResult = grid.get(row - 1);
                    }
                    case MODE_COL -> {
                        List<String> column = new ArrayList<>();
                        for (List<String> cells : grid) {
                            if (col - 1 < cells.size()) column.add(cells.get(col - 1));
                        }
                        listResult = column;
                    }
                    case MODE_BY_HEADER -> {
                        String value = findHeader(grid, name, row);
                        if (value == null) {
                            errorPin.setValue(new PinString("表头中找不到列名：" + name));
                            markUnachieved();
                            executeNext(runnable, elsePin);
                            return;
                        }
                        result = value;
                    }
                    case MODE_SHEETS -> listResult = List.of("CSV（单表）");
                    default -> {
                        if (row - 1 < grid.size() && col - 1 < grid.get(row - 1).size()) {
                            result = grid.get(row - 1).get(col - 1);
                        }
                    }
                }
            } else {
                // 工作表名填了优先于序号
                if (!sheetName.isEmpty()) {
                    List<String> names = ExcelUtil.readSheetNames(path);
                    int found = names.indexOf(sheetName);
                    if (found < 0) {
                        errorPin.setValue(new PinString("找不到工作表：" + sheetName));
                        markUnachieved();
                        executeNext(runnable, elsePin);
                        return;
                    }
                    sheet = found + 1;
                }
                Map<String, String> cells = ExcelUtil.readCells(path, sheet);
                maxRowPin.getValue(PinInteger.class).setValue(maxRow(cells));
                maxColPin.getValue(PinInteger.class).setValue(maxCol(cells));
                String sep = colSep.isEmpty() ? "\t" : colSep;
                switch (mode) {
                    case MODE_ALL -> {
                        StringBuilder builder = new StringBuilder();
                        for (int r = 1; r <= maxRow(cells); r++) {
                            if (r > 1) builder.append('\n');
                            for (int c = 1; c <= maxCol(cells); c++) {
                                if (c > 1) builder.append(sep);
                                builder.append(cells.getOrDefault(r + ":" + c, ""));
                            }
                        }
                        result = builder.toString();
                    }
                    case MODE_ROW -> {
                        List<String> line = new ArrayList<>();
                        for (int c = 1; c <= maxCol(cells); c++) {
                            String value = cells.get(row + ":" + c);
                            if (value != null) line.add(value);
                        }
                        listResult = line;
                    }
                    case MODE_COL -> {
                        List<String> column = new ArrayList<>();
                        for (int r = 1; r <= maxRow(cells); r++) {
                            String value = cells.get(r + ":" + col);
                            if (value != null) column.add(value);
                        }
                        listResult = column;
                    }
                    case MODE_BY_HEADER -> {
                        // 首行为表头：按单元格内容找列号
                        int headerCol = -1;
                        for (String key : cells.keySet()) {
                            if (key.startsWith("1:") && name.equals(cells.get(key))) {
                                headerCol = Integer.parseInt(key.split(":")[1]);
                                break;
                            }
                        }
                        if (headerCol < 0) {
                            errorPin.setValue(new PinString("表头中找不到列名：" + name));
                            markUnachieved();
                            executeNext(runnable, elsePin);
                            return;
                        }
                        result = cells.getOrDefault(row + ":" + headerCol, "");
                    }
                    case MODE_SHEETS -> listResult = ExcelUtil.readSheetNames(path);
                    default -> result = cells.getOrDefault(row + ":" + col, "");
                }
            }

            if (listResult != null) {
                PinList list = listPin.getValue(PinList.class);
                StringBuilder builder = new StringBuilder();
                for (String item : listResult) {
                    list.add(new PinString(item));
                    if (builder.length() > 0) builder.append('\n');
                    builder.append(item);
                }
                result = builder.toString();
            }
            resultPin.setValue(new PinString(result));
            successPin.getValue(PinBoolean.class).setValue(true);
            executeNext(runnable, outPin);
        } catch (Exception e) {
            errorPin.setValue(new PinString(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            markUnachieved();
            executeNext(runnable, elsePin);
        }
    }

    // CSV 按表头取值：首行找列名
    private String findHeader(List<List<String>> grid, String name, int row) {
        if (grid.isEmpty()) return null;
        int headerCol = -1;
        for (int i = 0; i < grid.get(0).size(); i++) {
            if (name.equals(grid.get(0).get(i))) {
                headerCol = i;
                break;
            }
        }
        if (headerCol < 0) return null;
        if (row - 1 >= grid.size() || headerCol >= grid.get(row - 1).size()) return "";
        return grid.get(row - 1).get(headerCol);
    }

    private int maxRow(Map<String, String> cells) {
        int max = 0;
        for (String key : cells.keySet()) max = Math.max(max, Integer.parseInt(key.split(":")[0]));
        return max;
    }

    private int maxCol(Map<String, String> cells) {
        int max = 0;
        for (String key : cells.keySet()) max = Math.max(max, Integer.parseInt(key.split(":")[1]));
        return max;
    }

    // 行号针脚：读单元格/读一行/读一列/按表头取值模式显示
    private static class RowShowablePin extends NotLinkAblePin {
        public RowShowablePin(PinBase value, int titleId) {
            super(value, titleId, false, false, true);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof ReadExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            int index = mode != null ? mode.getIndex() : 0;
            return index == MODE_CELL || index == MODE_ROW || index == MODE_COL || index == MODE_BY_HEADER;
        }
    }

    // 列号针脚：读单元格/读一行/读一列/按表头取值模式显示
    private static class ColShowablePin extends NotLinkAblePin {
        public ColShowablePin(PinBase value, int titleId) {
            super(value, titleId, false, false, true);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof ReadExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            int index = mode != null ? mode.getIndex() : 0;
            return index == MODE_CELL || index == MODE_ROW || index == MODE_COL || index == MODE_BY_HEADER;
        }
    }

    // A1 引用针脚：与行号/列号相同的显示条件
    private static class RowColShowablePin extends NotLinkAblePin {
        public RowColShowablePin(PinBase value, int titleId) {
            super(value, titleId, false, false, true);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof ReadExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            int index = mode != null ? mode.getIndex() : 0;
            return index == MODE_CELL || index == MODE_ROW || index == MODE_COL || index == MODE_BY_HEADER;
        }
    }

    // 列名针脚：仅按表头取值模式显示
    private static class NameShowablePin extends ShowAblePin {
        public NameShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof ReadExcelAction action)) return false;
            PinSingleSelect mode = action.modePin.getValue();
            return mode != null && mode.getIndex() == MODE_BY_HEADER;
        }
    }
}
