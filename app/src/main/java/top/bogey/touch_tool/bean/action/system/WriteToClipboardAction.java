package top.bogey.touch_tool.bean.action.system;

import android.graphics.Bitmap;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

public class WriteToClipboardAction extends ExecuteAction {
    private final transient Pin textPin = new Pin(new PinString(), R.string.pin_string);

    public WriteToClipboardAction() {
        super(ActionType.WRITE_TO_CLIPBOARD);
        addPin(textPin);
    }

    public WriteToClipboardAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPin(textPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinObject object = getPinValue(runnable, textPin);
        MainApplication instance = MainApplication.getInstance();
        if (object instanceof PinImage pinImage) {
            Bitmap bitmap = pinImage.getImage();
            if (bitmap != null) {
                AppUtil.copyToClipboard(instance, bitmap, false);
            }
        } else if (!object.toString().isEmpty()) {
            AppUtil.copyToClipboard(instance, object.toString(), false);
        }
        executeNext(runnable, outPin);
    }
}
