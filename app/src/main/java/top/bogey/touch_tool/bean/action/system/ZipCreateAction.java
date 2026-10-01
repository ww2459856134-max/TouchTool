package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 压缩文件或目录为 ZIP
public class ZipCreateAction extends ExecuteAction {
    private final transient Pin sourcePin = new Pin(new PinString(), R.string.file_action_source_path);
    private final transient Pin targetPin = new Pin(new PinString(), R.string.file_action_target_path);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public ZipCreateAction() {
        super(ActionType.ZIP_CREATE);
        addPins(sourcePin, targetPin, resultPin);
    }

    public ZipCreateAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(sourcePin, targetPin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String source = getPinValue(runnable, sourcePin).toString();
        String target = getPinValue(runnable, targetPin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(source) && FileUtil.checkPath(target)) {
            File from = new File(source.trim());
            File to = new File(target.trim());
            if (from.exists()) {
                File parent = to.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    executeNext(runnable, outPin);
                    return;
                }
                try (ZipOutputStream zipStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(to)))) {
                    // 目录压缩时条目以目录名开头，单文件时以文件名为根
                    String root = from.isDirectory() ? from.getName() + "/" : from.getName();
                    boolean success = zipFile(zipStream, from, root);
                    resultPin.getValue(PinBoolean.class).setValue(success);
                } catch (IOException ignored) {
                }
            }
        }
        executeNext(runnable, outPin);
    }

    // 递归写入 ZIP 条目
    private boolean zipFile(ZipOutputStream zipStream, File file, String entryName) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            if (children.length == 0) {
                zipStream.putNextEntry(new ZipEntry(entryName));
                zipStream.closeEntry();
                return true;
            }
            boolean success = true;
            for (File child : children) {
                if (!zipFile(zipStream, child, entryName + child.getName() + (child.isDirectory() ? "/" : ""))) {
                    success = false;
                }
            }
            return success;
        }
        zipStream.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) > 0) {
                zipStream.write(buffer, 0, length);
            }
        }
        zipStream.closeEntry();
        return true;
    }
}
