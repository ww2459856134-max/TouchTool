package top.bogey.touch_tool.bean.action.image;

import android.graphics.Bitmap;

import com.google.gson.JsonObject;
import com.king.zxing.util.CodeUtils;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

public class ParseQRCodeAction extends ExecuteAction {
    private final transient Pin imagePin = new Pin(new PinImage(), R.string.parse_qrcode_action_image);
    private final transient Pin contentPin = new Pin(new PinString(), R.string.parse_qrcode_action_content, true);

    public ParseQRCodeAction() {
        super(ActionType.PARSE_QRCODE);
        addPins(imagePin, contentPin);
    }

    public ParseQRCodeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(imagePin, contentPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinImage image = getPinValue(runnable, imagePin);
        Bitmap bitmap = image.getImage();
        if (bitmap == null) return;
        String content = CodeUtils.parseQRCode(bitmap);
        contentPin.getValue(PinString.class).setValue(content);
        executeNext(runnable, outPin);
    }
}
