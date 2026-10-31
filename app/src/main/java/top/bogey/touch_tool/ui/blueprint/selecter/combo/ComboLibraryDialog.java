package top.bogey.touch_tool.ui.blueprint.selecter.combo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.combo.ActionCombo;
import top.bogey.touch_tool.bean.combo.ComboSaver;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.DisplayUtil;

// 卡片组合库面板：内置组合在前，点击插入任务，长按删除自定义组合
public class ComboLibraryDialog extends android.app.Dialog {

    public interface OnPickListener {
        void onPick(ActionCombo combo);
    }

    public ComboLibraryDialog(@NonNull Context context, OnPickListener listener) {
        super(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) DisplayUtil.dp2px(context, 20);
        root.setPadding(pad, pad, pad, pad);

        TextView titleView = new TextView(context);
        titleView.setText(R.string.combo_library);
        titleView.setTextSize(17);
        titleView.setPadding(0, 0, 0, pad / 2);
        root.addView(titleView);

        TextView hintView = new TextView(context);
        hintView.setText(R.string.combo_library_tips);
        hintView.setTextSize(12);
        hintView.setPadding(0, 0, 0, pad);
        root.addView(hintView);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) DisplayUtil.dp2px(context, 380)));

        java.util.List<ActionCombo> combos = ComboSaver.getInstance().getCombos();
        if (combos.isEmpty()) {
            TextView emptyView = new TextView(context);
            emptyView.setText(R.string.combo_library_empty);
            emptyView.setTextSize(13);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, pad * 2, 0, pad * 2);
            list.addView(emptyView);
        }

        for (ActionCombo combo : combos) {
            list.addView(createItem(context, combo, listener));
        }

        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private View createItem(Context context, ActionCombo combo, OnPickListener listener) {
        int pad = (int) DisplayUtil.dp2px(context, 12);

        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(pad, pad, pad, pad);
        item.setBackgroundResource(R.drawable.variable_action_bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = pad / 2;
        item.setLayoutParams(params);

        TextView titleView = new TextView(context);
        String title = combo.getTitle();
        if (combo.isBuiltin()) title += "  ★内置";
        titleView.setText(title);
        titleView.setTextSize(15);
        item.addView(titleView);

        TextView descView = new TextView(context);
        String desc = combo.getDesc() == null || combo.getDesc().isEmpty() ? "（无备注）" : combo.getDesc();
        descView.setText(String.format("%s · %d 张卡片%s", desc, combo.getActions().size(),
                combo.getVariables().isEmpty() ? "" : " · " + combo.getVariables().size() + " 个变量"));
        descView.setTextSize(12);
        descView.setPadding(0, pad / 4, 0, 0);
        item.addView(descView);

        item.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onPick(combo);
        });

        if (!combo.isBuiltin()) {
            item.setOnLongClickListener(v -> {
                AppUtil.showDialog(context, context.getString(R.string.combo_delete_confirm, combo.getTitle()), result -> {
                    if (result) {
                        ComboSaver.getInstance().removeCombo(combo.getId());
                        dismiss();
                        new ComboLibraryDialog(context, listener).show();
                    }
                });
                return true;
            });
        }
        return item;
    }
}
