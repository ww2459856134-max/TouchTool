package top.bogey.touch_tool.ui.blueprint.pin_slot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.databinding.PinSlotNormalBinding;
import top.bogey.touch_tool.utils.DisplayUtil;

@SuppressLint("ViewConstructor")
public class ExecutePinSlotView extends PinSlotView {
    private final PinSlotNormalBinding binding;

    public ExecutePinSlotView(@NonNull Context context, Pin pin) {
        super(context, pin);
        binding = PinSlotNormalBinding.inflate(LayoutInflater.from(context), this, true);
        binding.getRoot().setStrokeColor(getPinColor());
        float cornerSize1 = DisplayUtil.dp2px(context, 4f);
        float cornerSize2 = DisplayUtil.dp2px(context, 8.5f);
        binding.getRoot().setShapeAppearanceModel(ShapeAppearanceModel.builder()
                .setTopLeftCorner(CornerFamily.ROUNDED, cornerSize1)
                .setTopRightCorner(CornerFamily.ROUNDED, cornerSize1)
                .setBottomLeftCorner(CornerFamily.CUT, cornerSize2)
                .setBottomRightCorner(CornerFamily.CUT, cornerSize2)
                .build());
    }

    @Override
    public void setLinked(boolean linked) {
        binding.getRoot().setCardBackgroundColor(linked ? getPinColor() : DisplayUtil.getAttrColor(getContext(), com.google.android.material.R.attr.colorSurfaceVariant));
        binding.getRoot().setStrokeColor(getPinColor());
    }
}
