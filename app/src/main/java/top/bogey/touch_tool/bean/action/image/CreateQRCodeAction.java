package top.bogey.touch_tool.bean.action.image;

import android.graphics.Bitmap;

import com.google.gson.JsonObject;
import com.king.zxing.util.CodeUtils;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

public class CreateQRCodeAction extends ExecuteAction {
    private final transient Pin contentPin = new Pin(new PinString(), R.string.create_qrcode_action_content);
    private final transient Pin sizePin = new Pin(new PinInteger(512), R.string.create_qrcode_action_size);
    private final transient Pin imagePin = new Pin(new PinImage(), R.string.create_qrcode_action_qrcode, true);

    public CreateQRCodeAction() {
        super(ActionType.CREATE_QRCODE);
        addPins(contentPin, sizePin, imagePin);
    }

    public CreateQRCodeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(contentPin, sizePin, imagePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinObject content = getPinValue(runnable, contentPin);
        PinInteger size = getPinValue(runnable, sizePin);
        if (!content.toString().isEmpty()) {
            Bitmap qrCode = CodeUtils.createQRCode(content.toString(), size.getValue());
            imagePin.getValue(PinImage.class).setImage(qrCode);
        }
        executeNext(runnable, outPin);
    }
}
