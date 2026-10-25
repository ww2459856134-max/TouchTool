package top.bogey.touch_tool.ui.blueprint.pin_slot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;

import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.databinding.PinSlotListBinding;
import top.bogey.touch_tool.utils.DisplayUtil;

@SuppressLint("ViewConstructor")
public class ListPinSlotView extends PinSlotView {
    private final PinSlotListBinding binding;

    public ListPinSlotView(@NonNull Context context, Pin pin) {
        super(context, pin);
        binding = PinSlotListBinding.inflate(LayoutInflater.from(context), this, true);
        binding.getRoot().setStrokeColor(getPinColor());
    }

    @Override
    public void setLinked(boolean linked) {
        binding.getRoot().setCardBackgroundColor(linked ? getPinColor() : DisplayUtil.getAttrColor(getContext(), com.google.android.material.R.attr.colorSurfaceVariant));
        binding.getRoot().setStrokeColor(getPinColor());
    }
}
