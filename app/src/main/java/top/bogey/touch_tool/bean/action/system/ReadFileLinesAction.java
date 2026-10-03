package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinFilePathString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 按行读取文本文件
public class ReadFileLinesAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinFilePathString(), R.string.file_action_path);
    private final transient Pin linesPin = new Pin(new PinList(new PinString()), R.string.read_file_lines_action_lines, true);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public ReadFileLinesAction() {
        super(ActionType.READ_FILE_LINES);
        addPins(pathPin, linesPin, resultPin);
    }

    public ReadFileLinesAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, linesPin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(path)) {
            File file = new File(path.trim());
            if (file.isFile() && file.length() <= 10 * 1024 * 1024) {
                try (FileInputStream stream = new FileInputStream(file)) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = stream.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                    }
                    String content = new String(output.toByteArray(), StandardCharsets.UTF_8);
                    List<String> lines = new ArrayList<>();
                    for (String line : content.split("\n", -1)) {
                        lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
                    }
                    // 末尾换行产生的空行去掉
                    if (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
                        lines.remove(lines.size() - 1);
                    }
                    FileUtil.setStringListValue(linesPin, lines);
                    resultPin.getValue(PinBoolean.class).setValue(true);
                } catch (IOException ignored) {
                }
            }
        }
        executeNext(runnable, outPin);
    }
}
