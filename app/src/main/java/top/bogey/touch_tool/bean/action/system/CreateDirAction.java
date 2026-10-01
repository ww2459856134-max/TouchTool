package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.File;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 创建目录
public class CreateDirAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinString(), R.string.file_action_path);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public CreateDirAction() {
        super(ActionType.CREATE_DIR);
        addPins(pathPin, resultPin);
    }

    public CreateDirAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(path)) {
            File dir = new File(path.trim());
            // 目录已存在也算成功
            resultPin.getValue(PinBoolean.class).setValue(dir.isDirectory() || dir.mkdirs());
        }
        executeNext(runnable, outPin);
    }
}
