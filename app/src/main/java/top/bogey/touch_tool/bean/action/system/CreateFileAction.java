package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinFilePathString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 创建空文件
public class CreateFileAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinFilePathString(), R.string.file_action_path);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public CreateFileAction() {
        super(ActionType.CREATE_FILE);
        addPins(pathPin, resultPin);
    }

    public CreateFileAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(path)) {
            File file = new File(path.trim());
            File parent = file.getParentFile();
            try {
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    executeNext(runnable, outPin);
                    return;
                }
                resultPin.getValue(PinBoolean.class).setValue(file.createNewFile() || file.isFile());
            } catch (IOException ignored) {
            }
        }
        executeNext(runnable, outPin);
    }
}
