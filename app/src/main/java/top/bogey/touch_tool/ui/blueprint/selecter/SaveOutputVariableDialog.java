package top.bogey.touch_tool.ui.blueprint.selecter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinAdd;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.save.variable.VariableSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.bean.task.Variable;
import top.bogey.touch_tool.ui.custom.dialog.EditVariableDialog;

// 快速存变量对话框：两步选择——先选卡片哪个输出值，再选存进哪个变量（可新建）
// 绑定关系保存在输出针脚的 saveVarId 上，运行时由 Action.saveOutputVariables 自动写入，画布上零新增卡片
public class SaveOutputVariableDialog {

    private static final int COLOR_GREEN = 0xFF2E8B57;
    private static final int COLOR_GREEN_BG = 0xFFE3F3EA;
    private static final int COLOR_GRAY = 0xFF8A8F9C;
    private static final int COLOR_GRAY_BG = 0xFFEFF1F5;
    private static final int COLOR_RED = 0xFFC25555;
    private static final int COLOR_RED_BG = 0xFFF9ECEC;
    private static final int COLOR_TEXT = 0xFF2B2F36;
    private static final int COLOR_SUB = 0xFF9AA0AC;
    private static final int COLOR_ACCENT = 0xFF4A5AA8;

    private final Context context;
    private final Task task;
    private final Action action;

    public SaveOutputVariableDialog(Context context, Task task, Action action) {
        this.context = context;
        this.task = task;
        this.action = action;
    }

    public void show() {
        List<Pin> pins = new ArrayList<>();
        for (Pin pin : action.getPins()) {
            if (isSaveAblePin(pin)) pins.add(pin);
        }

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(4);

        if (pins.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.save_output_none);
            empty.setTextSize(14);
            empty.setTextColor(COLOR_SUB);
            empty.setPadding(dp(24), dp(20), dp(24), dp(20));
            layout.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.save_output_title)
                    .setView(layout)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(layout);
        boolean first = true;
        for (Pin pin : pins) {
            if (!first) layout.addView(createDivider());
            first = false;
            layout.addView(createPinItem(pin));
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.save_output_select_pin_format, action.getTitle()))
                .setView(scrollView)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // 可绑定的输出针脚：非执行线、非隐藏、非添加占位
    public static boolean isSaveAblePin(Pin pin) {
        return pin.isOut() && !pin.isVertical() && !pin.isHide() && !(pin.getValue() instanceof PinAdd);
    }

    // 第一步：输出针脚行——左侧名称+类型+上次运行值预览，右侧绑定状态徽章，点击进入第二步
    @NonNull
    private LinearLayout createPinItem(Pin pin) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(20), dp(14), dp(16), dp(14));
        item.setBackground(ripple());

        LinearLayout textBox = new LinearLayout(context);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setText(pin.getTitle());
        title.setTextSize(15.5f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TEXT);
        textBox.addView(title);

        StringBuilder sub = new StringBuilder();
        if (pin.getValue() != null) {
            sub.append(pin.getValue().getClass().getSimpleName().replace("Pin", ""));
        }
        String lastRun = pin.getLastRunValue();
        if (lastRun != null && !lastRun.isEmpty()) {
            if (sub.length() > 0) sub.append(" · ");
            sub.append(lastRun);
        }
        if (sub.length() > 0) {
            TextView subtitle = new TextView(context);
            subtitle.setText(sub.toString());
            subtitle.setTextSize(12);
            subtitle.setTextColor(COLOR_SUB);
            subtitle.setSingleLine(true);
            subtitle.setPadding(0, dp(2), 0, 0);
            textBox.addView(subtitle);
        }
        item.addView(textBox);

        item.addView(createBadge(getPinItemText(pin), pin.getSaveVarId() != null && !pin.getSaveVarId().isEmpty()));
        item.setOnClickListener(v -> showVariableSelect(pin));
        return item;
    }

    private String getPinItemText(Pin pin) {
        String varId = pin.getSaveVarId();
        if (varId == null || varId.isEmpty()) {
            return context.getString(R.string.save_output_unbound);
        }
        Variable variable = task.upFindVariable(varId);
        if (variable == null) variable = VariableSaver.getInstance().getVar(varId);
        if (variable == null) return context.getString(R.string.save_output_var_missing);
        return String.format("→ %s", variable.getTitle());
    }

    // 状态徽章：绿底=已绑定，灰底=未绑定
    @NonNull
    private TextView createBadge(String text, boolean bound) {
        TextView badge = new TextView(context);
        badge.setText(text);
        badge.setTextSize(12);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(bound ? COLOR_GREEN : COLOR_GRAY);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackground(rounded(bound ? COLOR_GREEN_BG : COLOR_GRAY_BG, dp(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(10));
        badge.setLayoutParams(lp);
        return badge;
    }

    // 第二步：选择变量（任务变量 + 全局变量），带值预览与当前绑定勾选，可新建、可清除绑定
    private void showVariableSelect(Pin pin) {
        List<Variable> items = collectVariables();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        String boundId = pin.getSaveVarId();
        boolean first = true;
        for (Variable variable : items) {
            if (!first) layout.addView(createDivider());
            first = false;
            layout.addView(createVariableItem(pin, variable, variable.getId().equals(boundId)));
        }

        if (!first) layout.addView(createDivider());
        layout.addView(createActionItem(context.getString(R.string.save_output_new), COLOR_ACCENT, v -> showNewVariable(pin)));

        if (boundId != null && !boundId.isEmpty()) {
            layout.addView(createDivider());
            layout.addView(createActionItem(context.getString(R.string.save_output_clear), COLOR_RED, v -> {
                pin.setSaveVarId(null);
                saveTask();
                toast(context.getString(R.string.save_output_cleared));
            }));
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(layout);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.save_output_select_var)
                .setView(scrollView)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @NonNull
    private List<Variable> collectVariables() {
        List<Variable> items = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        if (task != null) {
            for (Variable variable : task.getVariables()) {
                if (variable.getTitle() == null || !ids.add(variable.getId())) continue;
                items.add(variable);
            }
        }
        for (Variable variable : VariableSaver.getInstance().getVars()) {
            if (variable.getTitle() == null || !ids.add(variable.getId())) continue;
            items.add(variable);
        }
        return items;
    }

    // 变量行：名称+值预览，当前绑定的显示 ✓
    @NonNull
    private LinearLayout createVariableItem(Pin pin, Variable variable, boolean bound) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(20), dp(13), dp(16), dp(13));
        item.setBackground(ripple());

        LinearLayout textBox = new LinearLayout(context);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setText(variable.getTitle());
        title.setTextSize(15.5f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(bound ? COLOR_GREEN : COLOR_TEXT);
        textBox.addView(title);

        String preview = getDisplayValue(variable);
        TextView subtitle = new TextView(context);
        subtitle.setText(preview);
        subtitle.setTextSize(12);
        subtitle.setTextColor(COLOR_SUB);
        subtitle.setSingleLine(true);
        subtitle.setPadding(0, dp(2), 0, 0);
        textBox.addView(subtitle);

        item.addView(textBox);

        if (bound) {
            TextView check = new TextView(context);
            check.setText("✓");
            check.setTextSize(16);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setTextColor(COLOR_GREEN);
            check.setPadding(dp(10), 0, 0, 0);
            item.addView(check);
        }

        item.setOnClickListener(v -> {
            pin.setSaveVarId(variable.getId());
            saveTask();
            toast(String.format(context.getString(R.string.save_output_saved), pin.getTitle(), variable.getTitle()));
        });
        return item;
    }

    @NonNull
    private LinearLayout createActionItem(String text, int color, android.view.View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(context);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(20), dp(14), dp(16), dp(14));
        item.setBackground(ripple());

        TextView title = new TextView(context);
        title.setText(text);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(color);
        item.addView(title);
        item.setOnClickListener(listener);
        return item;
    }

    private void showNewVariable(Pin pin) {
        PinObject value = pin.getValue();
        Variable variable = value == null ? new Variable(new PinString())
                : new Variable((PinObject) value.copy());
        EditVariableDialog dialog = new EditVariableDialog(context, variable);
        dialog.setTitle(R.string.variable_add);
        dialog.setCallback(result -> {
            if (!result) return;
            task.addVariable(variable);
            variable.save();
            pin.setSaveVarId(variable.getId());
            saveTask();
            toast(String.format(context.getString(R.string.save_output_saved), pin.getTitle(), variable.getTitle()));
        });
        dialog.show();
    }

    private void saveTask() {
        if (task != null) task.save();
    }

    private void toast(String msg) {
        Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // 值预览：优先运行时保存值，为空回退配置值
    private String getDisplayValue(Variable variable) {
        String text = valueToString(variable.getSaveValue());
        if (text == null) text = valueToString(variable.getValue());
        return text == null ? context.getString(R.string.select_variable_value_empty) : text;
    }

    private String valueToString(PinObject value) {
        if (value == null || value.getClass().equals(PinObject.class)) return null;
        String result = value.toString();
        return result.isEmpty() ? null : result;
    }

    private TextView createDivider() {
        TextView divider = new TextView(context);
        divider.setBackground(rounded(0xFFE8EAF0, 0));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return divider;
    }

    private GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        if (radiusPx > 0) drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private android.graphics.drawable.RippleDrawable ripple() {
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.WHITE);
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x14000000), content, null);
    }

    private int dp(int value) {
        return (int) (context.getResources().getDisplayMetrics().density * value);
    }
}
