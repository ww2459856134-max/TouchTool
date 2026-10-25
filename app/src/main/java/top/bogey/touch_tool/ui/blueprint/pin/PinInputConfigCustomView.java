package top.bogey.touch_tool.ui.blueprint.pin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.PinInfo;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.PinType;
import top.bogey.touch_tool.databinding.PinLeftCustomBinding;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;

@SuppressLint("ViewConstructor")
public class PinInputConfigCustomView extends PinCustomView {
    private final PinLeftCustomBinding binding;

    public PinInputConfigCustomView(@NonNull Context context, ActionCard card, Pin pin) {
        super(context, card, pin);
        binding = PinLeftCustomBinding.inflate(LayoutInflater.from(context), this, true);
        pinInfoMap.remove(PinType.APPS);
        pinInfoMap.remove(PinType.APP);
        pinInfoMap.remove(PinType.NODE);

        List<PinInfo> pinInfos = pinInfoMap.get(PinType.NUMBER);
        pinInfoMap.put(PinType.NUMBER, filterPinInfo(pinInfos, PinSubType.DATE, PinSubType.TIME, PinSubType.PERIODIC));

        pinInfos = pinInfoMap.get(PinType.STRING);
        pinInfoMap.put(PinType.STRING, filterPinInfo(pinInfos, PinSubType.RINGTONE, PinSubType.FILE_CONTENT, PinSubType.SINGLE_LINE));
        init();
    }

    private List<PinInfo> filterPinInfo(List<PinInfo> pinInfo, PinSubType... subTypes) {
        if (pinInfo != null) {
            Set<PinSubType> set = new HashSet<>(Arrays.asList(subTypes));
            for (int i = pinInfo.size() - 1; i >= 0; i--) {
                PinInfo info = pinInfo.get(i);
                if (set.contains(info.getSubType())) {
                    pinInfo.remove(i);
                }
            }
        }
        return pinInfo;
    }

    @Override
    public MaterialButton getKeyTypeView() {
        return binding.keySlot;
    }

    @Override
    public MaterialButton getValueTypeView() {
        return binding.valueSlot;
    }

    @Override
    public MaterialButton getTypeView() {
        return binding.typeSpinner;
    }

    @Override
    public EditText getTitleEdit() {
        return binding.title;
    }

    @Override
    public MaterialButton getVisibleButton() {
        return binding.visibleButton;
    }

    @Override
    public Button getRemoveButton() {
        return binding.removeButton;
    }

    @Override
    public ViewGroup getSlotBox() {
        return binding.pinSlotBox;
    }

    @Override
    public TextView getTitleView() {
        return null;
    }

    @Override
    public ViewGroup getWidgetBox() {
        return binding.pinBox;
    }

    @Override
    public Button getCopyAndPasteButton() {
        return null;
    }

    @Override
    public void refreshPin() {
        super.refreshPin();
        binding.pinBox.setVisibility(pin.isLinked() ? GONE : VISIBLE);
    }

    @Override
    public void expand(Action.ExpandType expandType) {
//        super.expand(expandType);
    }
}
