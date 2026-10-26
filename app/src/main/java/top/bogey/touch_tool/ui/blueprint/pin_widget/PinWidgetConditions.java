package top.bogey.touch_tool.ui.blueprint.pin_widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.node.NodeCondition;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinConditionsString;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.utils.DisplayUtil;
import top.bogey.touch_tool.utils.listener.TextChangedListener;

// 附加条件编辑控件：按钮显示条件数量，点击弹出结构化编辑对话框
// 每行条件 = [字段▼][运算符▼][值]，支持增删与上下移动，行间 AND/OR 由「匹配逻辑」针脚控制
public class PinWidgetConditions extends PinWidget<PinConditionsString> {
    private static final String[] STATUS_KEYS = {"visible", "clickable", "selected", "scrollable", "enabled"};

    private final MaterialButton button;

    public PinWidgetConditions(@NonNull Context context, ActionCard card, PinView pinView, PinConditionsString pinBase, boolean custom) {
        super(context, card, pinView, pinBase, custom);
        button = new MaterialButton(getContext());
        button.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        button.setOnClickListener(v -> showEditDialog());
        addView(button);
        refreshSummary();
    }

    @Override
    protected void initBase() {
    }

    @Override
    protected void initCustom() {
    }

    private List<NodeCondition> getConditions() {
        return NodeCondition.fromJson(pinBase.toString());
    }

    private void refreshSummary() {
        int size = getConditions().size();
        String title = getContext().getString(R.string.pin_conditions);
        button.setText(size == 0 ? title + getContext().getString(R.string.node_condition_empty) : title + "（" + size + "）");
    }

    private String[] getFieldOptions() {
        return getContext().getResources().getStringArray(R.array.condition_field);
    }

    private String[] getOpOptions(int field) {
        if (field == NodeCondition.FIELD_LENGTH) return getContext().getResources().getStringArray(R.array.condition_num_op);
        if (field == NodeCondition.FIELD_STATUS) return getContext().getResources().getStringArray(R.array.condition_status_op);
        return getContext().getResources().getStringArray(R.array.node_match_mode);
    }

    private String[] getStatusOptions() {
        return getContext().getResources().getStringArray(R.array.condition_status);
    }

    private void showPopup(View anchor, String[] options, int selected, OpCallback callback) {
        ListPopupWindow popup = new ListPopupWindow(getContext());
        popup.setAdapter(new android.widget.ArrayAdapter<>(getContext(), R.layout.widget_textview_item, options));
        popup.setAnchorView(anchor);
        popup.setModal(true);
        popup.setOnItemClickListener((parent, view, position, id) -> {
            callback.onSelect(position);
            popup.dismiss();
        });
        popup.show();
    }

    private interface OpCallback {
        void onSelect(int which);
    }

    // 对话框内的行与列表控制器，生命周期与对话框一致
    private class ConditionEditor {
        final Context context = getContext();
        final LinearLayout listBox = new LinearLayout(context);
        final TextView countText = new TextView(context);
        final List<LinearLayout> rows = new ArrayList<>();

        String[] getFieldOptions() {
            return PinWidgetConditions.this.getFieldOptions();
        }

        String[] getOpOptions(int field) {
            return PinWidgetConditions.this.getOpOptions(field);
        }

        LinearLayout createRow(NodeCondition condition) {
            Context ctx = context;
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = (int) DisplayUtil.dp2px(ctx, 2);
            row.setPadding(0, pad, 0, pad);
            row.setTag(condition);

            MaterialButton fieldButton = new MaterialButton(ctx);
            fieldButton.setLayoutParams(new LinearLayout.LayoutParams((int) DisplayUtil.dp2px(ctx, 92), LinearLayout.LayoutParams.WRAP_CONTENT));
                fieldButton.setTextSize(13);
            fieldButton.setOnClickListener(v -> {
                String[] fields = getFieldOptions();
                showPopup(fieldButton, fields, condition.field, which -> {
                    condition.field = which;
                    fieldButton.setText(fields[which]);
                    refreshOp(row);
                    bindValue(row, condition);
                });
            });
            row.addView(fieldButton);

            MaterialButton opButton = new MaterialButton(ctx);
            opButton.setLayoutParams(new LinearLayout.LayoutParams((int) DisplayUtil.dp2px(ctx, 88), LinearLayout.LayoutParams.WRAP_CONTENT));
            opButton.setTextSize(13);
            row.addView(opButton);

            LinearLayout valueBox = new LinearLayout(ctx);
            valueBox.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(valueBox);

            MaterialButton upButton = new MaterialButton(ctx);
            upButton.setLayoutParams(new LinearLayout.LayoutParams((int) DisplayUtil.dp2px(ctx, 40), LinearLayout.LayoutParams.WRAP_CONTENT));
            upButton.setIconResource(R.drawable.icon_keyboard_arrow_up);
            upButton.setIconPadding(0);
            upButton.setPadding(0, 0, 0, 0);
            upButton.setOnClickListener(v -> moveRow(row, -1));
            row.addView(upButton);

            MaterialButton downButton = new MaterialButton(ctx);
            downButton.setLayoutParams(new LinearLayout.LayoutParams((int) DisplayUtil.dp2px(ctx, 40), LinearLayout.LayoutParams.WRAP_CONTENT));
            downButton.setIconResource(R.drawable.icon_keyboard_arrow_down);
            downButton.setIconPadding(0);
            downButton.setPadding(0, 0, 0, 0);
            downButton.setOnClickListener(v -> moveRow(row, 1));
            row.addView(downButton);

            MaterialButton removeButton = new MaterialButton(ctx);
            removeButton.setLayoutParams(new LinearLayout.LayoutParams((int) DisplayUtil.dp2px(ctx, 40), LinearLayout.LayoutParams.WRAP_CONTENT));
            removeButton.setIconResource(R.drawable.icon_close);
            removeButton.setIconPadding(0);
            removeButton.setPadding(0, 0, 0, 0);
            removeButton.setOnClickListener(v -> {
                rows.remove(row);
                listBox.removeView(row);
                refreshCount();
            });
            row.addView(removeButton);
            return row;
        }

        void addRow(NodeCondition condition) {
            LinearLayout row = createRow(condition);
            rows.add(row);
            listBox.addView(row);
            refreshField(row, condition);
            refreshOp(row);
            bindValue(row, condition);
        }

        void refreshField(LinearLayout row, NodeCondition condition) {
            MaterialButton fieldButton = (MaterialButton) row.getChildAt(0);
            fieldButton.setText(getFieldOptions()[condition.field]);
        }

        void refreshOp(LinearLayout row) {
            NodeCondition condition = (NodeCondition) row.getTag();
            MaterialButton opButton = (MaterialButton) row.getChildAt(1);
            String[] ops = getOpOptions(condition.field);
            if (condition.op < 0 || condition.op >= ops.length) {
                condition.op = condition.field == NodeCondition.FIELD_STATUS ? 0 : 1;
            }
            opButton.setText(ops[condition.op]);
            opButton.setOnClickListener(v -> showPopup(opButton, ops, condition.op, which -> {
                condition.op = which;
                opButton.setText(ops[which]);
            }));
        }

        void bindValue(LinearLayout row, NodeCondition condition) {
            LinearLayout valueBox = (LinearLayout) row.getChildAt(2);
            valueBox.removeAllViews();
            Context ctx = context;

            if (condition.field == NodeCondition.FIELD_STATUS) {
                MaterialButton valueButton = new MaterialButton(ctx);
                valueButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                valueButton.setTextSize(13);
                int statusIndex = 0;
                for (int i = 0; i < STATUS_KEYS.length; i++) {
                    if (STATUS_KEYS[i].equals(condition.value)) statusIndex = i;
                }
                final int selectedStatus = statusIndex;
                String[] statusOptions = getStatusOptions();
                valueButton.setText(statusOptions[statusIndex]);
                valueButton.setOnClickListener(v -> showPopup(valueButton, statusOptions, selectedStatus, which -> {
                    condition.value = STATUS_KEYS[which];
                    valueButton.setText(statusOptions[which]);
                }));
                valueBox.addView(valueButton);
            } else {
                EditText valueEdit = new EditText(ctx);
                valueEdit.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                valueEdit.setSingleLine(true);
                valueEdit.setTextSize(13);
                valueEdit.setInputType(condition.field == NodeCondition.FIELD_LENGTH ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
                valueEdit.setHint(ctx.getString(condition.field == NodeCondition.FIELD_LENGTH ? R.string.node_condition_length_hint : R.string.node_condition_value_hint));
                valueEdit.setText(condition.value == null ? "" : condition.value);
                valueEdit.addTextChangedListener(new TextChangedListener() {
                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        condition.value = s.toString();
                    }
                });
                valueBox.addView(valueEdit);
            }
        }

        void moveRow(LinearLayout row, int delta) {
            int index = rows.indexOf(row);
            int target = index + delta;
            if (index < 0 || target < 0 || target >= rows.size()) return;
            rows.remove(index);
            rows.add(target, row);
            listBox.removeView(row);
            listBox.addView(row, target);
        }

        void refreshCount() {
            countText.setText(context.getString(R.string.node_condition_count, rows.size()));
        }
    }

    @SuppressLint("SetTextI18n")
    private void showEditDialog() {
        Context context = getContext();
        ConditionEditor editor = new ConditionEditor();

        int pad = (int) DisplayUtil.dp2px(context, 16);
        int padSmall = (int) DisplayUtil.dp2px(context, 4);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, padSmall, pad, 0);

        editor.listBox.setOrientation(LinearLayout.VERTICAL);
        editor.listBox.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(editor.listBox);

        editor.countText.setTextSize(12);
        editor.countText.setPadding(padSmall, padSmall, padSmall, 0);
        root.addView(editor.countText);

        for (NodeCondition condition : getConditions()) {
            editor.addRow(condition);
        }
        editor.refreshCount();

        MaterialButton addButton = new MaterialButton(context);
        addButton.setText(R.string.node_condition_add);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addParams.setMargins(0, padSmall, 0, 0);
        addButton.setLayoutParams(addParams);
        addButton.setOnClickListener(v -> {
            editor.addRow(new NodeCondition(NodeCondition.FIELD_TEXT, 1, ""));
            editor.refreshCount();
        });
        root.addView(addButton);

        NestedScrollView scrollView = new NestedScrollView(context);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) DisplayUtil.dp2px(context, 340)));
        scrollView.addView(root);

        new AlertDialog.Builder(context)
                .setTitle(R.string.node_condition_title)
                .setView(scrollView)
                .setPositiveButton(R.string.node_condition_save, (dialog, which) -> {
                    List<NodeCondition> result = new ArrayList<>();
                    for (LinearLayout row : editor.rows) {
                        NodeCondition condition = (NodeCondition) row.getTag();
                        if (condition != null && !condition.isEmpty()) result.add(condition);
                    }
                    pinBase.setValue(NodeCondition.listToJson(result));
                    pinView.getPin().notifyValueUpdated(card.getTask());
                    refreshSummary();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
