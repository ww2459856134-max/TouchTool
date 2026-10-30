package top.bogey.touch_tool.ui.blueprint.selecter;

import android.content.Context;
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
        int pad = (int) (context.getResources().getDisplayMetrics().density * 4);
        layout.setPadding(pad, 0, pad, 0);

        if (pins.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.save_output_none);
            empty.setTextSize(14);
            empty.setPadding(pad * 3, pad * 3, pad * 3, pad * 3);
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
        for (Pin pin : pins) {
            layout.addView(createPinItem(pin, pad));
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.save_output_select_pin)
                .setView(scrollView)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // 可绑定的输出针脚：非执行线、非隐藏、非添加占位
    public static boolean isSaveAblePin(Pin pin) {
        return pin.isOut() && !pin.isVertical() && !pin.isHide() && !(pin.getValue() instanceof PinAdd);
    }

    // 第一步：列出所有输出针脚，显示已绑定的变量名，点击进入第二步
    @NonNull
    private TextView createPinItem(Pin pin, int pad) {
        TextView item = new TextView(context);
        item.setText(getPinItemText(pin));
        item.setTextSize(15);
        item.setPadding(pad * 3, pad, pad * 3, pad);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setOnClickListener(v -> showVariableSelect(pin));
        return item;
    }

    private String getPinItemText(Pin pin) {
        String title = pin.getTitle();
        String varId = pin.getSaveVarId();
        if (varId == null || varId.isEmpty()) {
            return String.format("%s   （%s）", title, context.getString(R.string.save_output_unbound));
        }
        Variable variable = task.upFindVariable(varId);
        if (variable == null) variable = VariableSaver.getInstance().getVar(varId);
        String name = variable != null ? variable.getTitle() : context.getString(R.string.save_output_var_missing);
        return String.format("%s   →  %s", title, name);
    }

    // 第二步：选择变量（任务变量 + 全局变量），可新建、可清除绑定
    private void showVariableSelect(Pin pin) {
        List<Variable> items = collectVariables();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (context.getResources().getDisplayMetrics().density * 4);
        layout.setPadding(pad, 0, pad, 0);

        for (Variable variable : items) {
            layout.addView(createVariableItem(pin, variable, pad));
        }
        layout.addView(createNewVariableItem(pin, pad));
        if (pin.getSaveVarId() != null && !pin.getSaveVarId().isEmpty()) {
            layout.addView(createClearItem(pin, pad));
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

    @NonNull
    private TextView createVariableItem(Pin pin, Variable variable, int pad) {
        TextView item = new TextView(context);
        item.setText(String.format("%s  =  %s", variable.getTitle(), getDisplayValue(variable)));
        item.setTextSize(15);
        item.setPadding(pad * 3, pad, pad * 3, pad);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setOnClickListener(v -> {
            pin.setSaveVarId(variable.getId());
            saveTask();
            toast(String.format(context.getString(R.string.save_output_saved), pin.getTitle(), variable.getTitle()));
        });
        return item;
    }

    @NonNull
    private TextView createNewVariableItem(Pin pin, int pad) {
        TextView item = new TextView(context);
        item.setText(R.string.save_output_new);
        item.setTextSize(15);
        item.setPadding(pad * 3, pad, pad * 3, pad);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setOnClickListener(v -> {
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
        });
        return item;
    }

    @NonNull
    private TextView createClearItem(Pin pin, int pad) {
        TextView item = new TextView(context);
        item.setText(R.string.save_output_clear);
        item.setTextSize(15);
        item.setPadding(pad * 3, pad, pad * 3, pad);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setOnClickListener(v -> {
            pin.setSaveVarId(null);
            saveTask();
            toast(context.getString(R.string.save_output_cleared));
        });
        return item;
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
}
