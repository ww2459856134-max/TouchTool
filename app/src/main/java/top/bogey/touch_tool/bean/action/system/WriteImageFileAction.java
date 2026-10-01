package top.bogey.touch_tool.bean.action.system;

import android.graphics.Bitmap;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 把图片保存为文件
public class WriteImageFileAction extends ExecuteAction {
    private final transient Pin imagePin = new Pin(new PinImage(), R.string.pin_image);
    private final transient Pin pathPin = new Pin(new PinString(), R.string.file_action_path);
    private final transient Pin formatPin = new Pin(new PinSingleSelect(R.array.image_file_format), R.string.write_image_file_action_format);
    private final transient Pin qualityPin = new Pin(new PinInteger(100), R.string.write_image_file_action_quality);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public WriteImageFileAction() {
        super(ActionType.WRITE_IMAGE_FILE);
        addPins(imagePin, pathPin, formatPin, qualityPin, resultPin);
    }

    public WriteImageFileAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(imagePin, pathPin, formatPin, qualityPin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(path)) {
            PinImage image = getPinValue(runnable, imagePin);
            Bitmap bitmap = image.getImage();
            File file = new File(path.trim());
            if (bitmap != null) {
                Bitmap.CompressFormat format = getPinValue(runnable, formatPin).toString().equals("JPEG")
                        ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
                int quality = 100;
                if (getPinValue(runnable, qualityPin) instanceof PinInteger pinInteger) {
                    quality = Math.max(0, Math.min(100, pinInteger.getValue()));
                }
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    executeNext(runnable, outPin);
                    return;
                }
                try (FileOutputStream stream = new FileOutputStream(file)) {
                    resultPin.getValue(PinBoolean.class).setValue(bitmap.compress(format, quality, stream));
                } catch (IOException ignored) {
                }
            }
        }
        executeNext(runnable, outPin);
    }
}
