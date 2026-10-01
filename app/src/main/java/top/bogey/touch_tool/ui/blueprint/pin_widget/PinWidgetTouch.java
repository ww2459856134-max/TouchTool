package top.bogey.touch_tool.ui.blueprint.pin_widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinTouchPath;
import top.bogey.touch_tool.databinding.PinWidgetTouchBinding;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;
import top.bogey.touch_tool.ui.blueprint.picker.TouchPickerPreview;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.ui.custom.TouchPathView;

@SuppressLint("ViewConstructor")
public class PinWidgetTouch extends PinWidget<PinTouchPath> {
    private final PinWidgetTouchBinding binding;

    public PinWidgetTouch(@NonNull Context context, ActionCard card, PinView pinView, PinTouchPath pinBase, boolean custom) {
        super(context, card, pinView, pinBase, custom);
        binding = PinWidgetTouchBinding.inflate(LayoutInflater.from(context), this, true);
        init();
    }

    @Override
    protected void initBase() {
        binding.pathView.setPath(pinBase.getPathParts());

        binding.pathView.setOnClickListener(v -> {
            TouchPathView view = new TouchPathView(getContext(), pinBase.getPathParts(), true);
            new MaterialAlertDialogBuilder(getContext())
                    .setPositiveButton(R.string.enter, null)
                    .setView(view)
                    .show();
        });

        binding.pickButton.setOnClickListener(v -> new TouchPickerPreview(getContext(), result -> {
            pinBase.setValue(result.getValue());
            pinBase.setAnchor(result.getAnchor());
            pinView.getPin().notifyValueUpdated(card.getTask());
            binding.pathView.setPath(result.getPathParts());
        }, pinBase).show());
    }

    @Override
    protected void initCustom() {

    }
}
