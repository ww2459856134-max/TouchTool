package top.bogey.touch_tool.ui.blueprint.selecter;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.save.variable.VariableSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.bean.task.Variable;
import top.bogey.touch_tool.utils.callback.ResultCallback;

// 变量选择对话框：列出当前任务与全局变量（带当前值预览），点选返回变量名，用于快速插入 {变量名}
public class SelectVariableDialog {

    private final Context context;
    private final Task task;
    private final ResultCallback<String> callback;

    public SelectVariableDialog(Context context, Task task, ResultCallback<String> callback) {
        this.context = context;
        this.task = task;
        this.callback = callback;
    }

    public void show() {
        List<Variable> items = collectVariables();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (context.getResources().getDisplayMetrics().density * 4);
        layout.setPadding(pad, 0, pad, 0);

        if (items.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.select_variable_empty);
            empty.setTextSize(14);
            empty.setPadding(pad * 3, pad * 3, pad * 3, pad * 3);
            layout.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(layout);
            for (Variable variable : items) {
                layout.addView(createItemView(variable));
            }

            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.select_variable_title)
                    .setView(scrollView)
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.select_variable_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @NonNull
    private List<Variable> collectVariables() {
        List<Variable> items = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (task != null) {
            for (Variable variable : task.getVariables()) {
                if (variable.getTitle() == null || !names.add(variable.getTitle())) continue;
                items.add(variable);
            }
        }
        for (Variable variable : VariableSaver.getInstance().getVars()) {
            if (variable.getTitle() == null || !names.add(variable.getTitle())) continue;
            items.add(variable);
        }
        return items;
    }

    @NonNull
    private TextView createItemView(Variable variable) {
        int pad = (int) (context.getResources().getDisplayMetrics().density * 12);
        TextView item = new TextView(context);
        item.setText(String.format("%s  =  %s", variable.getTitle(), getDisplayValue(variable)));
        item.setTextSize(15);
        item.setPadding(pad, pad, pad, pad);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setOnClickListener(v -> {
            if (callback != null) callback.onResult(variable.getTitle());
        });
        return item;
    }

    // 值预览：优先运行时保存值，为空回退配置值；无值基类与空串都视为空
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
