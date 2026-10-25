package top.bogey.touch_tool.ui.tool.watcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.button.MaterialButton;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.other.watcher.VariableWatcher;
import top.bogey.touch_tool.bean.save.variable.VariableSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.bean.task.Variable;
import top.bogey.touch_tool.databinding.FloatVariableWatcherBinding;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.DisplayUtil;
import top.bogey.touch_tool.utils.EAnchor;
import top.bogey.touch_tool.utils.float_window_manager.FloatInterface;
import top.bogey.touch_tool.utils.float_window_manager.FloatWindow;

@SuppressLint("ViewConstructor")
public class VariableWatcherFloatView extends android.widget.FrameLayout implements FloatInterface, VariableWatcher.OnHistoryListener {
    private final FloatVariableWatcherBinding binding;
    private final WatcherAdapter adapter = new WatcherAdapter();
    private final String tag = VariableWatcherFloatView.class.getName();

    private final Task task;
    private boolean globalScope = false;
    private boolean historyMode = false;

    private final android.os.Handler handler = new android.os.Handler();
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!historyMode) adapter.refreshValues();
            handler.postDelayed(this, 500);
        }
    };

    private int minWidth, minHeight, maxWidth, maxHeight;
    private int originWidth, originHeight;

    public VariableWatcherFloatView(@NonNull Context context, @Nullable Task task) {
        super(context);
        this.task = task;

        minWidth = (int) DisplayUtil.dp2px(context, 168);
        minHeight = (int) DisplayUtil.dp2px(context, 128);
        android.util.Size size = DisplayUtil.getScreenSize(context);
        maxWidth = size.getWidth();
        maxHeight = (int) (size.getHeight() * 0.8f);

        binding = FloatVariableWatcherBinding.inflate(LayoutInflater.from(context), this, true);

        binding.scopeButton.setOnClickListener(v -> {
            globalScope = !globalScope;
            updateScopeTitle();
            adapter.refreshValues();
        });
        updateScopeTitle();

        binding.historyButton.setOnClickListener(v -> {
            historyMode = !historyMode;
            binding.historyButton.setChecked(historyMode);
            if (historyMode) adapter.refreshHistory();
            else adapter.refreshValues();
        });

        binding.closeButton.setOnClickListener(v -> dismiss());

        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(context));

        VariableWatcher.getInstance().addListener(this);
    }

    private void updateScopeTitle() {
        if (globalScope || task == null) {
            binding.scopeButton.setText(R.string.variable_watcher_global);
        } else {
            binding.scopeButton.setText(task.getTitle());
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (originWidth == 0 || originHeight == 0) {
            originWidth = Math.max(1, getWidth());
            originHeight = Math.max(1, getHeight());
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // 只有标题栏可拖动窗口；列表区域彻底禁用拖动，长按/滚动永不冲突
            int[] location = new int[2];
            binding.scopeButton.getLocationOnScreen(location);
            boolean inTitleBar = new RectF(location[0], location[1],
                    location[0] + binding.scopeButton.getWidth(),
                    location[1] + binding.scopeButton.getHeight()).contains(event.getRawX(), event.getRawY());
            FloatWindow.setDragAble(tag, inTitleBar);
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public void show() {
        FloatWindow.with(MainApplication.getInstance().getService())
                .setLayout(this)
                .setTag(tag)
                .setSpecial(true)
                .setLocation(EAnchor.CENTER, 0, 0)
                .show();
        handler.postDelayed(pollRunnable, 500);
        adapter.refreshValues();
    }

    @Override
    public void dismiss() {
        handler.removeCallbacks(pollRunnable);
        VariableWatcher.getInstance().removeListener(this);
        FloatWindow.dismiss(tag);
    }

    @Override
    public void onHistoryChanged() {
        if (historyMode) post(() -> adapter.refreshHistory());
    }

    // 重置：运行值恢复为变量定义的初始默认值
    private void resetVariable(Variable variable) {
        if (variable == null || variable.getValue() == null) return;
        String oldValue = variable.getSaveValue() == null ? "" : variable.getSaveValue().toString();
        variable.setSaveValue((top.bogey.touch_tool.bean.pin.pin_objects.PinObject) variable.getValue().copy());
        if (globalScope || task == null) variable.save();
        VariableWatcher.getInstance().record(variable.getTitle(), oldValue,
                variable.getValue() == null ? "" : variable.getValue().toString(),
                getContext().getString(R.string.variable_watcher_manual_reset));
        adapter.refreshValues();
    }

    private class WatcherAdapter extends RecyclerView.Adapter<WatcherViewHolder> {
        private final List<String[]> items = new ArrayList<>();
        private final List<Variable> currentVars = new ArrayList<>();

        public void refreshValues() {
            List<String[]> newItems = new ArrayList<>();
            List<Variable> vars = globalScope || task == null
                    ? VariableSaver.getInstance().getVars()
                    : task.getVariables();
            currentVars.clear();
            for (Variable variable : vars) {
                String name = variable.getTitle();
                // 运行时值在 saveValue 字段（SetVariableAction 写入），latestValues 优先（覆盖运行副本场景）
                String value = VariableWatcher.getInstance().getLatestValue(name);
                if (value == null && variable.getSaveValue() != null) value = variable.getSaveValue().toString();
                if (value == null && variable.getValue() != null) value = variable.getValue().toString();
                if (value == null) value = "";
                if (value.length() > 60) value = value.substring(0, 60) + "…";
                currentVars.add(variable);
                newItems.add(new String[]{name, VariableWatcher.mask(name, value)});
            }
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        public void refreshHistory() {
            List<String[]> newItems = new ArrayList<>();
            List<VariableWatcher.WatchEvent> history = VariableWatcher.getInstance().getHistory();
            for (VariableWatcher.WatchEvent event : history) {
                String title = AppUtil.formatDateTime(getContext(), event.time, true, true)
                        + " " + event.varName + " (" + event.source + ")";
                String value = VariableWatcher.mask(event.varName, event.oldValue) + " → " + VariableWatcher.mask(event.varName, event.newValue);
                newItems.add(new String[]{title, value});
            }
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public WatcherViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout box = new LinearLayout(parent.getContext());
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int pad = (int) DisplayUtil.dp2px(parent.getContext(), 6);
            box.setPadding(pad, pad, pad, pad);

            LinearLayout textBox = new LinearLayout(parent.getContext());
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView nameText = new TextView(parent.getContext());
            nameText.setTextSize(13);
            nameText.setTypeface(null, android.graphics.Typeface.BOLD);
            textBox.addView(nameText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView valueText = new TextView(parent.getContext());
            valueText.setTextSize(12);
            textBox.addView(valueText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            box.addView(textBox);

            android.content.Context ctx = parent.getContext();
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnParams.setMarginStart((int) DisplayUtil.dp2px(parent.getContext(), 4));

            TextView copyButton = new TextView(ctx);
            copyButton.setText("复制");
            copyButton.setTextSize(10);
            copyButton.setTextColor(android.graphics.Color.WHITE);
            copyButton.setBackgroundResource(R.drawable.variable_action_bg);
            copyButton.setPadding((int) DisplayUtil.dp2px(ctx, 7), (int) DisplayUtil.dp2px(ctx, 3), (int) DisplayUtil.dp2px(ctx, 7), (int) DisplayUtil.dp2px(ctx, 3));
            box.addView(copyButton, btnParams);

            TextView resetButton = new TextView(ctx);
            resetButton.setText("重置");
            resetButton.setTextSize(10);
            resetButton.setTextColor(android.graphics.Color.WHITE);
            resetButton.setBackgroundResource(R.drawable.variable_action_bg);
            resetButton.setPadding((int) DisplayUtil.dp2px(ctx, 7), (int) DisplayUtil.dp2px(ctx, 3), (int) DisplayUtil.dp2px(ctx, 7), (int) DisplayUtil.dp2px(ctx, 3));
            box.addView(resetButton, btnParams);

            return new WatcherViewHolder(box, nameText, valueText, copyButton, resetButton);
        }

        private String getItemText(Variable variable) {
            String value = VariableWatcher.getInstance().getLatestValue(variable.getTitle());
            if (value == null && variable.getSaveValue() != null) value = variable.getSaveValue().toString();
            if (value == null && variable.getValue() != null) value = variable.getValue().toString();
            return value == null ? "" : value;
        }

        @Override
        public void onBindViewHolder(@NonNull WatcherViewHolder holder, int position) {
            String[] item = items.get(position);
            holder.nameText.setText(item[0]);
            holder.valueText.setText(item[1]);

            holder.copyButton.setOnClickListener(v -> {
                if (position < 0 || position >= currentVars.size()) return;
                Variable variable = currentVars.get(position);
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("value", getItemText(variable)));
                    android.widget.Toast.makeText(getContext(), R.string.variable_watcher_copied, android.widget.Toast.LENGTH_SHORT).show();
                }
            });

            holder.resetButton.setOnClickListener(v -> {
                if (position < 0 || position >= currentVars.size()) return;
                resetVariable(currentVars.get(position));
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class WatcherViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView valueText;
        private final TextView copyButton;
        private final TextView resetButton;

        public WatcherViewHolder(@NonNull android.view.View itemView, TextView nameText, TextView valueText, TextView copyButton, TextView resetButton) {
            super(itemView);
            this.nameText = nameText;
            this.valueText = valueText;
            this.copyButton = copyButton;
            this.resetButton = resetButton;
        }
    }
}
