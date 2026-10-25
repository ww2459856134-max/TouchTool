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

import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.databinding.PinBottomCustomBinding;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;

@SuppressLint("ViewConstructor")
public class PinBottomCustomView extends PinCustomView {
    private final PinBottomCustomBinding binding;

    public PinBottomCustomView(@NonNull Context context, ActionCard card, Pin pin) {
        super(context, card, pin);
        binding = PinBottomCustomBinding.inflate(LayoutInflater.from(context), this, true);
        init();
    }

    @Override
    public MaterialButton getKeyTypeView() {
        return null;
    }

    @Override
    public MaterialButton getValueTypeView() {
        return null;
    }

    @Override
    public MaterialButton getTypeView() {
        return null;
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
}
