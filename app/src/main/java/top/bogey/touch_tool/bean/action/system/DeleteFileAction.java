package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.File;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinLong;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 删除文件或目录
public class DeleteFileAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinString(), R.string.file_action_path);
    private final transient Pin recursivePin = new Pin(new PinBoolean(), R.string.delete_file_action_recursive);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin countPin = new Pin(new PinLong(0), R.string.delete_file_action_count, true);

    public DeleteFileAction() {
        super(ActionType.DELETE_FILE);
        addPins(pathPin, recursivePin, resultPin, countPin);
    }

    public DeleteFileAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, recursivePin, resultPin, countPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        boolean recursive = getPinValue(runnable, recursivePin) instanceof PinBoolean pinBoolean && pinBoolean.getValue();
        resultPin.getValue(PinBoolean.class).setValue(false);
        countPin.getValue(PinLong.class).setValue(0L);
        // 安全校验：拒绝空路径与根目录，防止误删整个存储
        if (FileUtil.checkPathSafe(path)) {
            File file = new File(path.trim());
            if (file.isDirectory()) {
                if (recursive) {
                    long count = FileUtil.deleteRecursive(file);
                    if (count >= 0) {
                        resultPin.getValue(PinBoolean.class).setValue(true);
                        countPin.getValue(PinLong.class).setValue(count);
                    }
                }
            } else if (file.isFile()) {
                if (file.delete()) {
                    resultPin.getValue(PinBoolean.class).setValue(true);
                    countPin.getValue(PinLong.class).setValue(1L);
                }
            }
        }
        executeNext(runnable, outPin);
    }
}
