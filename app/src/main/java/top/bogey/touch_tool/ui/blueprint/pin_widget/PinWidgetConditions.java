package top.bogey.touch_tool.ui.blueprint.pin_widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListPopupWindow;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.node.NodeCondition;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinConditionsString;
import top.bogey.touch_tool.databinding.DialogNodeConditionsBinding;
import top.bogey.touch_tool.databinding.WidgetConditionItemBinding;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.utils.listener.TextChangedListener;

// 附加条件编辑控件：按钮显示条件数量，点击弹出 BottomSheet 结构化编辑器
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

    private void showEditDialog() {
        Context context = getContext();
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        DialogNodeConditionsBinding binding = DialogNodeConditionsBinding.inflate(LayoutInflater.from(context));
        dialog.setContentView(binding.getRoot());

        List<NodeCondition> conditions = new ArrayList<>(getConditions());
        ConditionAdapter adapter = new ConditionAdapter(context, conditions);
        binding.conditionBox.setLayoutManager(new LinearLayoutManager(context));
        binding.conditionBox.setAdapter(adapter);

        binding.countText.setText(context.getString(R.string.node_condition_count, conditions.size()));
        adapter.setCountListener(size -> binding.countText.setText(context.getString(R.string.node_condition_count, size)));

        binding.addButton.setOnClickListener(v -> {
            conditions.add(new NodeCondition(NodeCondition.FIELD_TEXT, 1, ""));
            adapter.notifyItemInserted(conditions.size() - 1);
            binding.conditionBox.scrollToPosition(conditions.size() - 1);
            adapter.notifyCountChanged();
        });

        binding.saveButton.setOnClickListener(v -> {
            List<NodeCondition> result = new ArrayList<>();
            for (NodeCondition condition : conditions) {
                if (condition != null && !condition.isEmpty()) result.add(condition);
            }
            pinBase.setValue(NodeCondition.listToJson(result));
            pinView.getPin().notifyValueUpdated(card.getTask());
            refreshSummary();
            dialog.dismiss();
        });

        dialog.show();
    }

    private interface OpCallback {
        void onSelect(int which);
    }

    // 条件行 RecyclerView 适配器
    private class ConditionAdapter extends RecyclerView.Adapter<ConditionAdapter.ViewHolder> {
        private final Context context;
        private final List<NodeCondition> conditions;
        private CountListener countListener;

        public ConditionAdapter(Context context, List<NodeCondition> conditions) {
            this.context = context;
            this.conditions = conditions;
        }

        public void setCountListener(CountListener listener) {
            countListener = listener;
        }

        public void notifyCountChanged() {
            if (countListener != null) countListener.onCount(conditions.size());
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            WidgetConditionItemBinding binding = WidgetConditionItemBinding.inflate(LayoutInflater.from(context), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.refresh(conditions.get(position));
        }

        @Override
        public int getItemCount() {
            return conditions.size();
        }

        private void showListPopup(View anchor, String[] options, OpCallback callback) {
            ListPopupWindow popup = new ListPopupWindow(context);
            popup.setAdapter(new android.widget.ArrayAdapter<>(context, R.layout.widget_textview_item, options));
            popup.setAnchorView(anchor);
            popup.setModal(true);
            popup.setOnItemClickListener((parent, view, position, id) -> {
                callback.onSelect(position);
                popup.dismiss();
            });
            popup.show();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final WidgetConditionItemBinding binding;

            public ViewHolder(@NonNull WidgetConditionItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;

                binding.upButton.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position <= 0) return;
                    conditions.add(position - 1, conditions.remove(position));
                    notifyItemMoved(position, position - 1);
                });

                binding.downButton.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position < 0 || position >= conditions.size() - 1) return;
                    conditions.add(position + 1, conditions.remove(position));
                    notifyItemMoved(position, position + 1);
                });

                binding.removeButton.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position < 0) return;
                    conditions.remove(position);
                    notifyItemRemoved(position);
                    notifyCountChanged();
                });
            }

            @SuppressLint("SetTextI18n")
            void refresh(NodeCondition condition) {
                String[] fields = getFieldOptions();
                binding.fieldButton.setText(fields[condition.field]);
                binding.fieldButton.setOnClickListener(v -> showListPopup(binding.fieldButton, fields, which -> {
                    condition.field = which;
                    binding.fieldButton.setText(fields[which]);
                    refreshOp(condition);
                    refreshValue(condition);
                }));
                refreshOp(condition);
                refreshValue(condition);
            }

            private void refreshOp(NodeCondition condition) {
                String[] ops = getOpOptions(condition.field);
                if (condition.op < 0 || condition.op >= ops.length) {
                    condition.op = condition.field == NodeCondition.FIELD_STATUS ? 0 : 1;
                }
                binding.opButton.setText(ops[condition.op]);
                binding.opButton.setOnClickListener(v -> showListPopup(binding.opButton, ops, which -> {
                    condition.op = which;
                    binding.opButton.setText(ops[which]);
                }));
            }

            private void refreshValue(NodeCondition condition) {
                binding.valueBox.removeAllViews();
                if (condition.field == NodeCondition.FIELD_STATUS) {
                    MaterialButton valueButton = new MaterialButton(context);
                    valueButton.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
                    valueButton.setTextSize(13);
                    int statusIndex = 0;
                    for (int i = 0; i < STATUS_KEYS.length; i++) {
                        if (STATUS_KEYS[i].equals(condition.value)) statusIndex = i;
                    }
                    String[] statusOptions = getStatusOptions();
                    valueButton.setText(statusOptions[statusIndex]);
                    valueButton.setOnClickListener(v -> showListPopup(valueButton, statusOptions, which -> {
                        condition.value = STATUS_KEYS[which];
                        valueButton.setText(statusOptions[which]);
                    }));
                    binding.valueBox.addView(valueButton);
                } else {
                    EditText valueEdit = new EditText(context);
                    valueEdit.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
                    valueEdit.setSingleLine(true);
                    valueEdit.setTextSize(13);
                    valueEdit.setInputType(condition.field == NodeCondition.FIELD_LENGTH ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
                    valueEdit.setHint(context.getString(condition.field == NodeCondition.FIELD_LENGTH ? R.string.node_condition_length_hint : R.string.node_condition_value_hint));
                    valueEdit.setText(condition.value == null ? "" : condition.value);
                    valueEdit.addTextChangedListener(new TextChangedListener() {
                        @Override
                        public void afterTextChanged(android.text.Editable s) {
                            condition.value = s.toString();
                        }
                    });
                    binding.valueBox.addView(valueEdit);
                }
            }
        }

        private interface CountListener {
            void onCount(int size);
        }
    }
}
