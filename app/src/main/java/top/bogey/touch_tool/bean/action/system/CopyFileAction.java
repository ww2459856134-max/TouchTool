package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.IOException;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 复制文件或目录
public class CopyFileAction extends ExecuteAction {
    private final transient Pin sourcePin = new Pin(new PinString(), R.string.file_action_source_path);
    private final transient Pin targetPin = new Pin(new PinString(), R.string.file_action_target_path);
    private final transient Pin overwritePin = new Pin(new PinBoolean(), R.string.file_action_overwrite);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public CopyFileAction() {
        super(ActionType.COPY_FILE);
        addPins(sourcePin, targetPin, overwritePin, resultPin);
    }

    public CopyFileAction(JsonObject jsonObject) {
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
            java.io.File from = new java.io.File(source.trim());
            java.io.File to = new java.io.File(target.trim());
            if (overwrite || !to.exists()) {
                try {
                    resultPin.getValue(PinBoolean.class).setValue(FileUtil.copyRecursive(from, to));
                } catch (IOException ignored) {
                }
            }
        }
        executeNext(runnable, outPin);
    }
}
