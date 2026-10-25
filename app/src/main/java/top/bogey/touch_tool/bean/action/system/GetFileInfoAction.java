package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.File;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinLong;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinFilePathString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 获取文件信息
public class GetFileInfoAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinFilePathString(), R.string.file_action_path);
    private final transient Pin existPin = new Pin(new PinBoolean(), R.string.get_file_info_action_exist, true);
    private final transient Pin dirPin = new Pin(new PinBoolean(), R.string.get_file_info_action_is_dir, true);
    private final transient Pin sizePin = new Pin(new PinLong(0), R.string.get_file_info_action_size, true);
    private final transient Pin modifiedPin = new Pin(new PinLong(0), R.string.get_file_info_action_modified, true);

    public GetFileInfoAction() {
        super(ActionType.GET_FILE_INFO);
        addPins(pathPin, existPin, dirPin, sizePin, modifiedPin);
    }

    public GetFileInfoAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, existPin, dirPin, sizePin, modifiedPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        existPin.getValue(PinBoolean.class).setValue(false);
        dirPin.getValue(PinBoolean.class).setValue(false);
        sizePin.getValue(PinLong.class).setValue(0L);
        modifiedPin.getValue(PinLong.class).setValue(0L);
        if (FileUtil.checkPath(path)) {
            File file = new File(path.trim());
            if (file.exists()) {
                existPin.getValue(PinBoolean.class).setValue(true);
                dirPin.getValue(PinBoolean.class).setValue(file.isDirectory());
                if (file.isFile()) {
                    sizePin.getValue(PinLong.class).setValue(file.length());
                }
                modifiedPin.getValue(PinLong.class).setValue(file.lastModified());
            }
        }
        executeNext(runnable, outPin);
    }
}
