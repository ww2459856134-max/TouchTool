package top.bogey.touch_tool.bean.action.system;

import android.content.Context;
import android.media.AudioManager;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.service.TaskRunnable;

public class GetVolumeAction extends CalculateAction {
    private final transient Pin volumeTypePin = new Pin(new PinSingleSelect(R.array.volume_type), R.string.get_volume_action);
    protected final transient Pin currentPin = new Pin(new PinInteger(), R.string.get_volume_action_current, true);
    protected final transient Pin maxPin = new Pin(new PinInteger(), R.string.get_volume_action_max, true, false, true);

    public GetVolumeAction() {
        super(ActionType.GET_VOLUME);
        addPins(volumeTypePin, currentPin, maxPin);
    }

    public GetVolumeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(volumeTypePin, currentPin, maxPin);
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
        Context context = MainApplication.getInstance();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        PinSingleSelect volumeType = getPinValue(runnable, volumeTypePin);
        int idx = volumeType.getIndex();
        int currentVolume = 0, maxVolume = 0;
        if (idx == 0) {
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        } else if (idx == 1) {
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
        } else if (idx == 2) {
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        }
        currentPin.getValue(PinInteger.class).setValue(currentVolume);
        maxPin.getValue(PinInteger.class).setValue(maxVolume);
    }
}
