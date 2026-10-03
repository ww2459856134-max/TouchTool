package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinDirPathString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 列出目录内容
public class ListDirAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinDirPathString(), R.string.file_action_path);
    private final transient Pin filesPin = new Pin(new PinList(new PinString()), R.string.list_dir_action_files, true);

    public ListDirAction() {
        super(ActionType.LIST_DIR);
        addPins(pathPin, filesPin);
    }

    public ListDirAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, filesPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        if (FileUtil.checkPath(path)) {
            List<String> strings = FileUtil.listDir(new File(path.trim()));
            FileUtil.setStringListValue(filesPin, strings);
        }
        executeNext(runnable, outPin);
    }
}
