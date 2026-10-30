package top.bogey.touch_tool.ui.blueprint.pin_widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.PinValueArea;
import top.bogey.touch_tool.databinding.PinWidgetValueAreaBinding;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.ui.blueprint.selecter.SelectVariableDialog;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.listener.TextChangedListener;

@SuppressLint("ViewConstructor")
public class PinWidgetValueArea extends PinWidget<PinValueArea> {
    private final PinWidgetValueAreaBinding binding;
    private boolean locked;

    public PinWidgetValueArea(@NonNull Context context, ActionCard card, PinView pinView, PinValueArea pinBase, boolean custom) {
        super(context, card, pinView, pinBase, custom);
        binding = PinWidgetValueAreaBinding.inflate(LayoutInflater.from(context), this, true);
        init();
    }

    @Override
    protected void initBase() {
        binding.lowEdit.setText(String.valueOf(pinBase.getMin()));
        binding.highEdit.setText(String.valueOf(pinBase.getMax()));
        locked = pinBase.getMin() == pinBase.getMax();
        binding.lockButton.setChecked(locked);
        binding.lockButton.setIconResource(locked ? R.drawable.icon_lock : R.drawable.icon_lock_open);
        binding.highEdit.setEnabled(!locked);

        binding.lowEdit.addTextChangedListener(new TextChangedListener() {
            @Override
            public void afterTextChanged(Editable s) {
                pinBase.setMin(toInt(s));
                if (binding.lockButton.isChecked()) {
                    binding.highEdit.setText(s);
                } else {
                    pinView.getPin().notifyValueUpdated(card.getTask());
                }
            }
        });

        binding.highEdit.addTextChangedListener(new TextChangedListener() {
            @Override
            public void afterTextChanged(Editable s) {
                pinBase.setMax(toInt(s));
                pinView.getPin().notifyValueUpdated(card.getTask());
            }
        });

        binding.lockButton.setOnClickListener(v -> {
            locked = !locked;
            binding.highEdit.setEnabled(!locked);
            binding.highEdit.setText(binding.lowEdit.getText());
            binding.lockButton.setChecked(locked);
            binding.lockButton.setIconResource(locked ? R.drawable.icon_lock : R.drawable.icon_lock_open);
        });

        // 区间快速插值：选变量绑定 {变量名}；已绑定时点击可编辑/清除完整表达式（如 {最小}~{最大}）
        refreshExpressionState();
        binding.varButton.setOnClickListener(v -> {
            String current = pinBase.getExpression();
            if (current == null || current.isEmpty()) {
                new SelectVariableDialog(getContext(), card.getTask(), name -> {
                    if (name == null || name.isEmpty()) return;
                    pinBase.setExpression("{" + name + "}");
                    refreshExpressionState();
                }).show();
            } else {
                AppUtil.showEditDialog(getContext(), R.string.pin_value_area_expression_edit, current, result -> {
                    String text = result == null ? "" : result.trim();
                    pinBase.setExpression(text.isEmpty() ? null : text);
                    refreshExpressionState();
                });
            }
        });
    }

    // 表达式状态刷新：绑定表达式时两个数值框禁用，避免误改
    private void refreshExpressionState() {
        String expression = pinBase.getExpression();
        boolean bound = expression != null && !expression.isEmpty();
        binding.lowEdit.setEnabled(!bound);
        binding.highEdit.setEnabled(!bound && !locked);
        if (bound) {
            binding.lowEdit.setText(expression);
            binding.highEdit.setText("");
        } else {
            binding.lowEdit.setText(String.valueOf(pinBase.getMin()));
            binding.highEdit.setText(String.valueOf(pinBase.getMax()));
        }
        binding.varButton.setIconTint(android.content.res.ColorStateList.valueOf(bound ? 0xFF2E8B57 : 0xFF8A8F9C));
    }

    @Override
    protected void initCustom() {

    }

    private int toInt(Editable s) {
        if (s == null || s.length() == 0) return 0;
        try {
            return Integer.parseInt(s.toString());
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
