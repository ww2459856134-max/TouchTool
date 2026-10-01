package top.bogey.touch_tool.bean.action.image;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

// 图像预处理：找图前对模板图和屏幕图做相同的灰度化、二值化或反色处理
// 两张图应用相同预处理后，光照、深色模式等差异对找图匹配的影响会大幅降低
public class ImagePreprocessAction extends ExecuteAction {
    private final transient Pin sourcePin = new Pin(new PinImage(), R.string.pin_image);
    private final transient Pin modePin = new NotLinkAblePin(new PinSingleSelect(R.array.image_preprocess_mode), R.string.image_preprocess_action_mode);
    private final transient Pin thresholdPin = new NotLinkAblePin(new PinInteger(128), R.string.image_preprocess_action_threshold);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin imagePin = new Pin(new PinImage(), R.string.pin_image, true);

    public ImagePreprocessAction() {
        super(ActionType.IMAGE_PREPROCESS);
        addPins(sourcePin, modePin, thresholdPin, successPin, imagePin);
    }

    public ImagePreprocessAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(sourcePin, modePin, thresholdPin, successPin, imagePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinImage source = getPinValue(runnable, sourcePin);
        PinSingleSelect mode = getPinValue(runnable, modePin);
        successPin.getValue(PinBoolean.class).setValue(false);

        Bitmap bitmap = source.getImage();
        if (bitmap != null) {
            Bitmap result = null;
            switch (mode.getIndex()) {
                case 0 -> result = toGrayscale(bitmap);
                case 1 -> {
                    int threshold = 128;
                    if (getPinValue(runnable, thresholdPin) instanceof PinInteger pinInteger) {
                        threshold = Math.max(0, Math.min(255, pinInteger.getValue()));
                    }
                    result = toBinary(bitmap, threshold);
                }
                case 2 -> result = invert(bitmap);
                default -> result = bitmap;
            }
            if (result != null) {
                imagePin.getValue(PinImage.class).setImage(result);
                successPin.getValue(PinBoolean.class).setValue(true);
            }
        }
        executeNext(runnable, outPin);
    }

    // 灰度化
    private Bitmap toGrayscale(Bitmap source) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        return drawWithColorMatrix(source, matrix);
    }

    // 反色
    private Bitmap invert(Bitmap source) {
        ColorMatrix matrix = new ColorMatrix(new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0
        });
        return drawWithColorMatrix(source, matrix);
    }

    private Bitmap drawWithColorMatrix(Bitmap source, ColorMatrix matrix) {
        Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0, 0, paint);
        return result;
    }

    // 二值化：先灰度化再按阈值分成黑白
    private Bitmap toBinary(Bitmap source, int threshold) {
        Bitmap gray = toGrayscale(source);
        int width = gray.getWidth();
        int height = gray.getHeight();
        int[] pixels = new int[width * height];
        gray.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i++) {
            // 灰度化后 R=G=B，取任一通道作为亮度值
            int brightness = pixels[i] & 0xFF;
            pixels[i] = brightness >= threshold ? 0xFFFFFFFF : 0xFF000000;
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }
}
