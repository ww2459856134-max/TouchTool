package top.bogey.touch_tool.bean.action.system;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.gson.JsonObject;

import java.io.File;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 从文件读取图片
public class ReadImageFileAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinString(), R.string.file_action_path);
    private final transient Pin imagePin = new Pin(new PinImage(), R.string.pin_image, true);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);

    public ReadImageFileAction() {
        super(ActionType.READ_IMAGE_FILE);
        addPins(pathPin, imagePin, resultPin);
    }

    public ReadImageFileAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, imagePin, resultPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        if (FileUtil.checkPath(path)) {
            File file = new File(path.trim());
            if (file.isFile()) {
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap != null) {
                    imagePin.getValue(PinImage.class).setImage(bitmap);
                    resultPin.getValue(PinBoolean.class).setValue(true);
                }
            }
        }
        executeNext(runnable, outPin);
    }
}
