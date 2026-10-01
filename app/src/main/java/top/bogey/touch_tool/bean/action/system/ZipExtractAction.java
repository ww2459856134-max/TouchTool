package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 解压 ZIP 到目录
public class ZipExtractAction extends ExecuteAction {
    private final transient Pin sourcePin = new Pin(new PinString(), R.string.file_action_source_path);
    private final transient Pin targetPin = new Pin(new PinString(), R.string.zip_extract_action_target_dir);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public ZipExtractAction() {
        super(ActionType.ZIP_EXTRACT);
        addPins(sourcePin, targetPin, resultPin);
    }

    public ZipExtractAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(sourcePin, targetPin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String source = getPinValue(runnable, sourcePin).toString();
        String target = getPinValue(runnable, targetPin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(source) && FileUtil.checkPath(target)) {
            File zipFile = new File(source.trim());
            File targetDir = new File(target.trim());
            if (zipFile.isFile() && (targetDir.isDirectory() || targetDir.mkdirs())) {
                try {
                    boolean success = extract(zipFile, targetDir);
                    resultPin.getValue(PinBoolean.class).setValue(success);
                } catch (IOException ignored) {
                }
            }
        }
        executeNext(runnable, outPin);
    }

    // 解压全部条目，条目路径必须是目标目录的子路径，防止路径穿越
    private boolean extract(File zipFile, File targetDir) throws IOException {
        boolean success = true;
        try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(zipFile))) {
            byte[] buffer = new byte[8192];
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                File file = new File(targetDir, entry.getName());
                if (!file.getCanonicalPath().startsWith(targetDir.getCanonicalPath() + File.separator)) {
                    success = false;
                    continue;
                }
                if (entry.isDirectory()) {
                    file.mkdirs();
                    continue;
                }
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    success = false;
                    continue;
                }
                try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
                    int length;
                    while ((length = zipStream.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                    }
                }
                zipStream.closeEntry();
            }
        }
        return success;
    }
}
