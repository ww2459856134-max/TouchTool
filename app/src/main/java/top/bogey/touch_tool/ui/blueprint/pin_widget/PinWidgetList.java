package top.bogey.touch_tool.ui.blueprint.pin_widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinMultiSelect;
import top.bogey.touch_tool.databinding.PinWidgetMultiSelectBinding;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.ui.blueprint.selecter.multi_select.MultiSelectDialog;

@SuppressLint("ViewConstructor")
public class PinWidgetList extends PinWidget<PinList> {
    private final PinWidgetMultiSelectBinding binding;

    public PinWidgetList(@NonNull Context context, ActionCard card, PinView pinView, PinList pinBase, boolean custom) {
        super(context, card, pinView, pinBase, custom);
        binding = PinWidgetMultiSelectBinding.inflate(LayoutInflater.from(context), this, true);
        init();
    }

    public PinWidgetList(@NonNull Context context, ActionCard card, PinView pinView, PinMultiSelect pinBase, boolean custom) {
        this(context, card, pinView, (PinList) pinBase, custom);
    }

    @Override
    protected void initBase() {
        binding.text.setText(pinBase.toString());
        binding.pickButton.setVisibility(GONE);
        if (pinBase.getSubType() == PinSubType.MULTI_SELECT) {
            binding.pickButton.setVisibility(VISIBLE);
            binding.pickButton.setOnClickListener(v -> {
                MultiSelectDialog dialog = new MultiSelectDialog(getContext(), (PinMultiSelect) pinBase);
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle(R.string.multi_select)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.enter, (d, which) -> {
                            pinBase.clear();
                            pinBase.addAll(dialog.getSelectedObjects());
                            binding.text.setText(pinBase.toString());
                        })
                        .setView(dialog)
                        .show();
            });
        }
    }

    @Override
    protected void initCustom() {

    }
}
