package top.bogey.touch_tool.bean.action.image;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

public class LoadImageAction extends CalculateAction {
    private final transient Pin filePin = new NotLinkAblePin(new PinImage(PinSubType.FILE_CONTENT), R.string.pin_image);
    // 图片路径/URL：支持 {变量} 插值，http/https 自动下载；非空时优先于上方相册图片
    private final transient Pin pathPin = new Pin(new PinString(), R.string.load_image_action_path);
    private final transient Pin imagePin = new Pin(new PinImage(), R.string.pin_boolean_result, true);
    private final transient Pin areaPin = new Pin(new PinArea(), R.string.pin_area, true);

    public LoadImageAction() {
        super(ActionType.LOAD_IMAGE);
        addPins(filePin, pathPin, imagePin, areaPin);
    }

    public LoadImageAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(filePin, pathPin, imagePin, areaPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        Bitmap bitmap = null;
        // 路径/URL 优先：支持 {变量} 插值，http/https 自动下载，其余按本地文件路径加载
        PinString path = getPinValue(runnable, pathPin);
        String pathText = path == null ? null : path.getValue();
        if (pathText != null && !pathText.trim().isEmpty()) {
            bitmap = loadFromPath(pathText.trim());
        }
        if (bitmap == null) {
            PinImage image = getPinValue(runnable, filePin);
            bitmap = image.getImage();
        }
        if (bitmap == null) return;
        imagePin.getValue(PinImage.class).setImage(bitmap);
        areaPin.getValue(PinArea.class).setValue(new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()));
    }

    // 从本地文件或网络 URL 加载图片；任何失败返回 null 不抛异常
    private Bitmap loadFromPath(String path) {
        try {
            byte[] bytes;
            if (path.startsWith("http://") || path.startsWith("https://")) {
                java.net.URL url = new java.net.URL(path);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                try (java.io.InputStream stream = connection.getInputStream();
                     java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                    byte[] chunk = new byte[8192];
                    int len;
                    while ((len = stream.read(chunk)) > 0) buffer.write(chunk, 0, len);
                    bytes = buffer.toByteArray();
                }
                connection.disconnect();
            } else {
                try (java.io.InputStream stream = new java.io.FileInputStream(path);
                     java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                    byte[] chunk = new byte[8192];
                    int len;
                    while ((len = stream.read(chunk)) > 0) buffer.write(chunk, 0, len);
                    bytes = buffer.toByteArray();
                }
            }
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
