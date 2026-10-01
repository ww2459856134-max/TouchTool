package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.File;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleLineString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 重命名文件或目录
public class RenameFileAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinString(), R.string.file_action_path);
    private final transient Pin namePin = new Pin(new PinSingleLineString(), R.string.rename_file_action_new_name);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public RenameFileAction() {
        super(ActionType.RENAME_FILE);
        addPins(pathPin, namePin, resultPin);
    }

    public RenameFileAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, namePin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        String name = getPinValue(runnable, namePin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(path) && FileUtil.checkPath(name)) {
            File file = new File(path.trim());
            File target = new File(file.getParentFile(), name.trim());
            // 目标已存在或原名与新名相同则不执行
            if (!target.exists() && !name.trim().equals(file.getName())) {
                resultPin.getValue(PinBoolean.class).setValue(file.renameTo(target));
            }
        }
        executeNext(runnable, outPin);
    }
}
