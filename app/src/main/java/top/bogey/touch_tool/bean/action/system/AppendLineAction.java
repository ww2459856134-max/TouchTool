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

// 追加一行文本到文件末尾
public class AppendLineAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinFilePathString(), R.string.file_action_path);
    private final transient Pin linePin = new Pin(new PinString(), R.string.append_line_action_line);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public AppendLineAction() {
        super(ActionType.APPEND_LINE);
        addPins(pathPin, linePin, resultPin);
    }

    public AppendLineAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, linePin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        String line = getPinValue(runnable, linePin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(path)) {
            File file = new File(path.trim());
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                executeNext(runnable, outPin);
                return;
            }
            try (FileOutputStream stream = new FileOutputStream(file, true)) {
                // 文件非空且末尾没有换行时先补一个换行
                boolean needNewLine = file.length() > 0;
                String text = (needNewLine ? "\n" : "") + line + "\n";
                stream.write(text.getBytes(StandardCharsets.UTF_8));
                resultPin.getValue(PinBoolean.class).setValue(true);
            } catch (IOException ignored) {
            }
        }
        executeNext(runnable, outPin);
    }
}
