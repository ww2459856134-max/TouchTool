package top.bogey.touch_tool.ui.play;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.databinding.FloatPlayHideChoiceBinding;
import top.bogey.touch_tool.utils.DisplayUtil;
import top.bogey.touch_tool.utils.EAnchor;
import top.bogey.touch_tool.utils.callback.ResultCallback;
import top.bogey.touch_tool.utils.float_window_manager.FloatInterface;
import top.bogey.touch_tool.utils.float_window_manager.FloatWindow;

@SuppressLint("ViewConstructor")
public class PlayFloatHideChoiceView extends FrameLayout implements FloatInterface {

    public PlayFloatHideChoiceView(@NonNull Context context, ResultCallback<Integer> callback) {
        super(context);
        FloatPlayHideChoiceBinding binding = FloatPlayHideChoiceBinding.inflate(LayoutInflater.from(context), this, true);

        binding.closeButton.setOnClickListener(v -> dismiss());

        String[] strings = getResources().getStringArray(R.array.manual_play_hide_type);
        for (int i = 0; i < strings.length - 1; i++) {
            MaterialButton button = new MaterialButton(context);
            button.setText(strings[i]);
            button.setTag(i);
            binding.buttonBox.addView(button);
            DisplayUtil.setViewWidth(button, ViewGroup.LayoutParams.MATCH_PARENT);

            button.setOnClickListener(v -> {
                callback.onResult((Integer) button.getTag());
                dismiss();
            });
        }
    }

    @Override
    public void show() {
        FloatWindow.with(MainApplication.getInstance().getService())
                .setTag(PlayFloatHideChoiceView.class.getName())
                .setLayout(this)
                .setLocation(EAnchor.CENTER, 0, 0)
                .setSpecial(true)
                .show();
    }

    @Override
    public void dismiss() {
        FloatWindow.dismiss(PlayFloatHideChoiceView.class.getName());
    }
}
