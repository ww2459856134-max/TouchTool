package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 移动文件或目录
public class MoveFileAction extends ExecuteAction {
    private final transient Pin sourcePin = new Pin(new PinString(), R.string.file_action_source_path);
    private final transient Pin targetPin = new Pin(new PinString(), R.string.file_action_target_path);
    private final transient Pin overwritePin = new Pin(new PinBoolean(), R.string.file_action_overwrite);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public MoveFileAction() {
        super(ActionType.MOVE_FILE);
        addPins(sourcePin, targetPin, overwritePin, resultPin);
    }

    public MoveFileAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(sourcePin, targetPin, overwritePin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String source = getPinValue(runnable, sourcePin).toString();
        String target = getPinValue(runnable, targetPin).toString();
        boolean overwrite = getPinValue(runnable, overwritePin) instanceof PinBoolean pinBoolean && pinBoolean.getValue();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(source) && FileUtil.checkPath(target)) {
            File from = new File(source.trim());
            File to = new File(target.trim());
            if (overwrite || !to.exists()) {
                File parent = to.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    executeNext(runnable, outPin);
                    return;
                }
                // 同一存储分区直接重命名
                if (from.renameTo(to)) {
                    resultPin.getValue(PinBoolean.class).setValue(true);
                } else {
                    // 跨分区降级为复制后删除源
                    try {
                        if (FileUtil.copyRecursive(from, to) && FileUtil.deleteRecursive(from) >= 0) {
                            resultPin.getValue(PinBoolean.class).setValue(true);
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        executeNext(runnable, outPin);
    }
}
