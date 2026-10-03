package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinFilePathString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 写入文本文件
public class WriteFileAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinFilePathString(), R.string.file_action_path);
    private final transient Pin contentPin = new Pin(new PinString(), R.string.file_action_content);
    private final transient Pin appendPin = new Pin(new PinBoolean(), R.string.write_file_action_append);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public WriteFileAction() {
        super(ActionType.WRITE_FILE);
        addPins(pathPin, contentPin, appendPin, resultPin);
    }

    public WriteFileAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, contentPin, appendPin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        String content = getPinValue(runnable, contentPin).toString();
        boolean append = getPinValue(runnable, appendPin) instanceof PinBoolean pinBoolean && pinBoolean.getValue();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(path)) {
            File file = new File(path.trim());
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                executeNext(runnable, outPin);
                return;
            }
            try (FileOutputStream stream = new FileOutputStream(file, append)) {
                stream.write(content.getBytes(StandardCharsets.UTF_8));
                resultPin.getValue(PinBoolean.class).setValue(true);
            } catch (IOException ignored) {
            }
        }
        executeNext(runnable, outPin);
    }
}
