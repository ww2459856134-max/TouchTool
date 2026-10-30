package top.bogey.touch_tool.ui.blueprint.pin_widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.text.Editable;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.databinding.PinWidgetAreaBinding;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;
import top.bogey.touch_tool.ui.blueprint.picker.AreaPickerPreview;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.ui.blueprint.selecter.SelectVariableDialog;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.listener.TextChangedListener;

@SuppressLint("ViewConstructor")
public class PinWidgetArea extends PinWidget<PinArea> {
    private final PinWidgetAreaBinding binding;

    public PinWidgetArea(@NonNull Context context, ActionCard card, PinView pinView, PinArea pinBase, boolean custom) {
        super(context, card, pinView, pinBase, custom);
        binding = PinWidgetAreaBinding.inflate(LayoutInflater.from(context), this, true);
        init();
    }

    @Override
    protected void initBase() {
        Rect area = pinBase.getValue();
        pinBase.setValue(area);
        binding.leftEdit.setText(String.valueOf(area.left));
        binding.topEdit.setText(String.valueOf(area.top));
        binding.rightEdit.setText(String.valueOf(area.right));
        binding.bottomEdit.setText(String.valueOf(area.bottom));

        binding.leftEdit.addTextChangedListener(new TextChangedListener() {
            @Override
            public void afterTextChanged(Editable s) {
                area.left = toInt(s);
                pinView.getPin().notifyValueUpdated(card.getTask());
            }
        });
        binding.topEdit.addTextChangedListener(new TextChangedListener() {
            @Override
            public void afterTextChanged(Editable s) {
                area.top = toInt(s);
                pinView.getPin().notifyValueUpdated(card.getTask());
            }
        });
        binding.rightEdit.addTextChangedListener(new TextChangedListener() {
            @Override
            public void afterTextChanged(Editable s) {
                area.right = toInt(s);
                pinView.getPin().notifyValueUpdated(card.getTask());
            }
        });
        binding.bottomEdit.addTextChangedListener(new TextChangedListener() {
            @Override
            public void afterTextChanged(Editable s) {
                area.bottom = toInt(s);
                pinView.getPin().notifyValueUpdated(card.getTask());
            }
        });

        binding.pickButton.setOnClickListener(v -> new AreaPickerPreview(getContext(), result -> {
            area.set(result);
            if (pinBase.getExpression() != null) {
                pinBase.setExpression(null);
                refreshEdits(area);
            }
            binding.leftEdit.setText(String.valueOf(area.left));
            binding.topEdit.setText(String.valueOf(area.top));
            binding.rightEdit.setText(String.valueOf(area.right));
            binding.bottomEdit.setText(String.valueOf(area.bottom));
        }, area).show());

        // 区域快速插值：选变量绑定 {变量名}；已绑定时点击可编辑/清除完整表达式
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
                AppUtil.showEditDialog(getContext(), R.string.pin_area_expression_edit, current, result -> {
                    String text = result == null ? "" : result.trim();
                    pinBase.setExpression(text.isEmpty() ? null : text);
                    refreshExpressionState();
                });
            }
        });
    }

    // 表达式状态刷新：绑定表达式时四个数值框禁用并显示占位，避免误改
    private void refreshExpressionState() {
        String expression = pinBase.getExpression();
        boolean bound = expression != null && !expression.isEmpty();
        binding.leftEdit.setEnabled(!bound);
        binding.topEdit.setEnabled(!bound);
        binding.rightEdit.setEnabled(!bound);
        binding.bottomEdit.setEnabled(!bound);
        if (bound) {
            binding.leftEdit.setText("≡");
            binding.topEdit.setText("");
            binding.rightEdit.setText("");
            binding.bottomEdit.setText(expression);
        } else {
            Rect area = pinBase.getValue();
            binding.leftEdit.setText(String.valueOf(area.left));
            binding.topEdit.setText(String.valueOf(area.top));
            binding.rightEdit.setText(String.valueOf(area.right));
            binding.bottomEdit.setText(String.valueOf(area.bottom));
        }
        binding.varButton.setIconTint(android.content.res.ColorStateList.valueOf(bound ? 0xFF2E8B57 : 0xFF8A8F9C));
    }

    private void refreshEdits(Rect area) {
        binding.leftEdit.setText(String.valueOf(area.left));
        binding.topEdit.setText(String.valueOf(area.top));
        binding.rightEdit.setText(String.valueOf(area.right));
        binding.bottomEdit.setText(String.valueOf(area.bottom));
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
