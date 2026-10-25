package top.bogey.touch_tool.ui.blueprint.pin_slot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinMap;
import top.bogey.touch_tool.databinding.PinSlotMapBinding;
import top.bogey.touch_tool.utils.DisplayUtil;

@SuppressLint("ViewConstructor")
public class MapPinSlotView extends PinSlotView {
    private final PinSlotMapBinding binding;

    public MapPinSlotView(@NonNull Context context, Pin pin) {
        super(context, pin);
        binding = PinSlotMapBinding.inflate(LayoutInflater.from(context), this, true);
        float cornerSize = DisplayUtil.dp2px(context, 2f);
        binding.keySlot.setStrokeColor(getPinKeyColor());
        binding.keySlot.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                .setTopLeftCorner(CornerFamily.ROUNDED, cornerSize)
                .setTopRightCorner(CornerFamily.CUT, 0)
                .setBottomLeftCorner(CornerFamily.ROUNDED, cornerSize)
                .setBottomRightCorner(CornerFamily.CUT, 0)
                .build());
        binding.valueSlot.setStrokeColor(getPinValueColor());
        binding.valueSlot.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                .setTopLeftCorner(CornerFamily.CUT, 0)
                .setTopRightCorner(CornerFamily.ROUNDED, cornerSize)
                .setBottomLeftCorner(CornerFamily.CUT, 0)
                .setBottomRightCorner(CornerFamily.ROUNDED, cornerSize)
                .build());
    }

    @Override
    public void setLinked(boolean linked) {
        int keyColor = linked ? getPinKeyColor() : DisplayUtil.getAttrColor(getContext(), com.google.android.material.R.attr.colorSurfaceVariant);
        binding.keySlot.setCardBackgroundColor(keyColor);
        binding.keySlot.setStrokeColor(getPinKeyColor());

        int valueColor = linked ? getPinValueColor() : DisplayUtil.getAttrColor(getContext(), com.google.android.material.R.attr.colorSurfaceVariant);
        binding.valueSlot.setCardBackgroundColor(valueColor);
        binding.valueSlot.setStrokeColor(getPinValueColor());
    }

    private @ColorInt int getPinKeyColor() {
        PinBase pinBase = pin.getValue();
        if (pin.linkAble() && pinBase instanceof PinMap pinMap) {
            return getPinColor(new Pin(pinMap.getKeyType()));
        }
        return Color.GRAY;
    }

    private @ColorInt int getPinValueColor() {
        PinBase pinBase = pin.getValue();
        if (pin.linkAble() && pinBase instanceof PinMap pinMap) {
            return getPinColor(new Pin(pinMap.getValueType()));
        }
        return Color.GRAY;
    }
}
