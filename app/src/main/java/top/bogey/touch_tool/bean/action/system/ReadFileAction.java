package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 读取文本文件
public class ReadFileAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinString(), R.string.file_action_path);
    private final transient Pin contentPin = new Pin(new PinString(), R.string.file_action_content, true);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public ReadFileAction() {
        super(ActionType.READ_FILE);
        addPins(pathPin, contentPin, resultPin);
    }

    public ReadFileAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, contentPin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(path)) {
            File file = new File(path.trim());
            if (file.isFile()) {
                // 限制单次读取大小，避免大文件撑爆内存
                if (file.length() <= 10 * 1024 * 1024) {
                    try (FileInputStream stream = new FileInputStream(file)) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = stream.read(buffer)) > 0) {
                            output.write(buffer, 0, length);
                        }
                        contentPin.setValue(new PinString(new String(output.toByteArray(), StandardCharsets.UTF_8)));
                        resultPin.getValue(PinBoolean.class).setValue(true);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        executeNext(runnable, outPin);
    }
}
