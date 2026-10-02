package top.bogey.touch_tool.bean.action.file;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinCodeString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// SQL 执行：对 SQLite 数据库执行 SQL 语句，SELECT 返回查询结果，其他语句返回受影响状态
// 支持建表/插入/更新/删除/查询；数据库文件不存在时自动创建
public class SqlExecuteAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinString(), R.string.sql_action_path);
    private final transient Pin sqlPin = new Pin(new PinCodeString(), R.string.sql_action_sql);
    private final transient Pin countPin = new Pin(new PinInteger(0), R.string.sql_action_count, true);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.pin_string, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public SqlExecuteAction() {
        super(ActionType.SQL_EXECUTE);
        addPins(pathPin, sqlPin, countPin, resultPin, successPin, elsePin);
    }

    public SqlExecuteAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, sqlPin, countPin, resultPin, successPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        String sql = getPinValue(runnable, sqlPin).toString();

        successPin.getValue(PinBoolean.class).setValue(false);
        countPin.getValue(PinInteger.class).setValue(0);
        resultPin.setValue(new PinString(""));

        if (path.isEmpty() || sql.isEmpty()) {
            executeNext(runnable, elsePin);
            return;
        }

        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            // 数据库文件不存在时自动创建
            database = SQLiteDatabase.openOrCreateDatabase(path, null);

            if (sql.trim().toLowerCase().startsWith("select")) {
                // 查询：列间制表符、行间换行
                cursor = database.rawQuery(sql, null);
                StringBuilder builder = new StringBuilder();
                int count = 0;
                while (cursor.moveToNext()) {
                    if (count > 0) builder.append('\n');
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        if (i > 0) builder.append('\t');
                        String value = cursor.getString(i);
                        builder.append(value == null ? "" : value);
                    }
                    count++;
                }
                countPin.getValue(PinInteger.class).setValue(count);
                resultPin.setValue(new PinString(builder.toString()));
            } else {
                // 建表/插入/更新/删除等非查询语句，支持分号分隔多条
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) database.execSQL(trimmed);
                }
                countPin.getValue(PinInteger.class).setValue(1);
            }

            successPin.getValue(PinBoolean.class).setValue(true);
            executeNext(runnable, outPin);
        } catch (Exception e) {
            resultPin.setValue(new PinString(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            executeNext(runnable, elsePin);
        } finally {
            if (cursor != null) cursor.close();
            if (database != null) database.close();
        }
    }
}
