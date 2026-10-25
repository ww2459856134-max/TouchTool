package top.bogey.touch_tool.ui.task;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.save.TagSaver;
import top.bogey.touch_tool.databinding.ViewTagListBinding;
import top.bogey.touch_tool.ui.MainActivity;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.DisplayUtil;
import top.bogey.touch_tool.utils.ui.DragViewHolderHelper;

public class TagView extends BottomSheetDialog {
    private final TaskView taskView;

    public TagView(@NonNull Context context, TaskView taskView) {
        super(context);
        this.taskView = taskView;

        ViewTagListBinding binding = ViewTagListBinding.inflate(LayoutInflater.from(context));
        setContentView(binding.getRoot());

        BottomSheetBehavior<FrameLayout> behavior = getBehavior();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        TagViewAdapter adapter = new TagViewAdapter(taskView);
        binding.tagBox.setAdapter(adapter);
        FlexboxLayoutManager layoutManager = (FlexboxLayoutManager) binding.tagBox.getLayoutManager();
        if (layoutManager != null) {
            layoutManager.setFlexWrap(FlexWrap.WRAP);
            layoutManager.setFlexDirection(FlexDirection.ROW);
        }
        DragViewHolderHelper helper = new DragViewHolderHelper(DragViewHolderHelper.HORIZONTAL | DragViewHolderHelper.VERTICAL, adapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(helper);
        touchHelper.attachToRecyclerView(binding.tagBox);

        binding.addButton.setOnClickListener(v -> AppUtil.showEditDialog(context, R.string.task_tag_add, "", result -> {
            if (result != null && !result.isEmpty()) {
                TagSaver.getInstance().addTag(result);
                adapter.addTag(result);
            }
        }));

        MainActivity activity = MainApplication.getInstance().getActivity();
        View decorView = activity.getWindow().getDecorView();
        behavior.setMaxWidth(decorView.getWidth());
        DisplayUtil.setViewHeight(binding.getRoot(), (int) (decorView.getHeight() * 0.75f));
    }

    @Override
    public void onDetachedFromWindow() {
        taskView.unselectAll();
        taskView.hideBottomBar();
        super.onDetachedFromWindow();
    }
}
