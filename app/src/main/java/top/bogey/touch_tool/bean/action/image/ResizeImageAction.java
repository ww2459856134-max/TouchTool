package top.bogey.touch_tool.bean.action.image;

import android.graphics.Bitmap;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinFloat;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.service.TaskRunnable;

public class ResizeImageAction extends ExecuteAction {
    private final transient Pin sourcePin = new Pin(new PinImage(), R.string.pin_image);
    private final transient Pin scalePin = new Pin(new PinFloat(1), R.string.resize_image_action_scale);
    private final transient Pin imagePin = new Pin(new PinImage(), R.string.pin_image, true);

    public ResizeImageAction() {
        super(ActionType.RESIZE_IMAGE);
        addPins(sourcePin, scalePin, imagePin);
    }

    public ResizeImageAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(sourcePin, scalePin, imagePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinImage source = getPinValue(runnable, sourcePin);
        PinNumber<?> scale = getPinValue(runnable, scalePin);

        Bitmap bitmap = source.getImage();
        if (bitmap != null) {
            int width = (int) (bitmap.getWidth() * scale.floatValue());
            int height = (int) (bitmap.getHeight() * scale.floatValue());
            if (width > 0 && height > 0) {
                Bitmap resizeBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                imagePin.getValue(PinImage.class).setImage(resizeBitmap);
            }
        }
        executeNext(runnable, outPin);
    }
}
