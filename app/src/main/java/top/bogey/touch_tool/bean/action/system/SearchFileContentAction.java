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
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 搜索文件内容中的关键字
public class SearchFileContentAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinString(), R.string.file_action_path);
    private final transient Pin keywordPin = new Pin(new PinString(), R.string.search_file_content_action_keyword);
    private final transient Pin foundPin = new Pin(new PinBoolean(), R.string.search_file_content_action_found, true);
    private final transient Pin linePin = new Pin(new PinInteger(0), R.string.search_file_content_action_line_number, true);
    private final transient Pin contentPin = new Pin(new PinString(), R.string.search_file_content_action_line_content, true);
    private final transient Pin countPin = new Pin(new PinInteger(0), R.string.search_file_content_action_count, true);

    public SearchFileContentAction() {
        super(ActionType.SEARCH_FILE_CONTENT);
        addPins(pathPin, keywordPin, foundPin, linePin, contentPin, countPin);
    }

    public SearchFileContentAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, keywordPin, foundPin, linePin, contentPin, countPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        String keyword = getPinValue(runnable, keywordPin).toString();
        foundPin.getValue(PinBoolean.class).setValue(false);
        linePin.getValue(PinInteger.class).setValue(0);
        contentPin.setValue(new PinString(""));
        countPin.getValue(PinInteger.class).setValue(0);
        if (FileUtil.checkPath(path) && !keyword.isEmpty()) {
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
                    int count = 0;
                    int firstLine = 0;
                    String firstLineContent = "";
                    String[] lines = content.split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        String line = lines[i].endsWith("\r") ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
                        if (line.contains(keyword)) {
                            count++;
                            if (firstLine == 0) {
                                firstLine = i + 1;
                                firstLineContent = line;
                            }
                        }
                    }
                    if (count > 0) {
                        foundPin.getValue(PinBoolean.class).setValue(true);
                        linePin.getValue(PinInteger.class).setValue(firstLine);
                        contentPin.setValue(new PinString(firstLineContent));
                        countPin.getValue(PinInteger.class).setValue(count);
                    }
                } catch (IOException ignored) {
                }
            }
        }
        executeNext(runnable, outPin);
    }
}
