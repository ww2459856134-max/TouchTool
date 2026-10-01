package top.bogey.touch_tool.ui.setting.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButtonToggleGroup;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.databinding.WidgetSettingSelectBinding;
import top.bogey.touch_tool.databinding.WidgetSettingSelectButtonHorizontalBinding;

public class SettingWidgetSelect extends FrameLayout {
    private final WidgetSettingSelectBinding binding;

    public SettingWidgetSelect(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        binding = WidgetSettingSelectBinding.inflate(LayoutInflater.from(context), this, true);

        try (TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.SettingWidgetSelect)) {
            int icon = typedArray.getResourceId(R.styleable.SettingWidgetSelect_icon, 0);
            binding.icon.setImageResource(icon);

            String title = typedArray.getString(R.styleable.SettingWidgetSelect_title);
            binding.title.setText(title);

            String description = typedArray.getString(R.styleable.SettingWidgetSelect_description);
            binding.description.setText(description);
            binding.description.setVisibility(description == null || description.isEmpty() ? GONE : VISIBLE);

            int options = typedArray.getResourceId(R.styleable.SettingWidgetSelect_options, 0);
            for (String option : getResources().getStringArray(options)) {
                WidgetSettingSelectButtonHorizontalBinding buttonBinding = WidgetSettingSelectButtonHorizontalBinding.inflate(LayoutInflater.from(context), binding.group, true);
                buttonBinding.getRoot().setText(option);
                buttonBinding.getRoot().setId(generateViewId());
            }
            binding.group.check(binding.group.getChildAt(0).getId());
        }
    }

    @Override
    public void setOnClickListener(OnClickListener listener) {
        binding.getRoot().setOnClickListener(listener);
    }

    public void setOnButtonCheckedListener(MaterialButtonToggleGroup.OnButtonCheckedListener listener) {
        binding.group.addOnButtonCheckedListener(listener);
    }

    public void check(int id) {
        binding.group.check(id);
    }

    public void checkIndex(int index) {
        View child = binding.group.getChildAt(index);
        if (child == null) return;
        check(child.getId());
    }
}
