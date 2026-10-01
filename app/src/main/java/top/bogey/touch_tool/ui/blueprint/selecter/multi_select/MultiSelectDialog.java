package top.bogey.touch_tool.ui.blueprint.selecter.multi_select;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinMultiSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.databinding.DialogMultiSelectBinding;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.listener.TextChangedListener;

@SuppressLint("ViewConstructor")
public class MultiSelectDialog extends FrameLayout {
    private final MultiSelectDialogAdapter adapter;

    public MultiSelectDialog(@NonNull Context context, PinMultiSelect multiSelect) {
        super(context);
        DialogMultiSelectBinding binding = DialogMultiSelectBinding.inflate(LayoutInflater.from(context), this, true);

        adapter = new MultiSelectDialogAdapter(multiSelect);
        binding.selectionBox.setAdapter(adapter);

        binding.searchEdit.addTextChangedListener(new TextChangedListener() {
            @Override
            public void afterTextChanged(Editable s) {
                String searchString = s.toString();
                List<PinMultiSelect.MultiSelectObject> selectObjects = multiSelect.getSelectObjects();
                if (searchString.isEmpty()) {
                    adapter.refreshObjects(selectObjects);
                } else {
                    List<PinMultiSelect.MultiSelectObject> newObjects = new ArrayList<>();
                    for (PinMultiSelect.MultiSelectObject selectObject : selectObjects) {
                        if (AppUtil.isStringContains(selectObject.title(), searchString)) newObjects.add(selectObject);
                        else if (AppUtil.isStringContains(selectObject.description(), searchString)) newObjects.add(selectObject);
                        else if (AppUtil.isStringContains(String.valueOf(selectObject.value()), searchString)) newObjects.add(selectObject);
                    }
                    adapter.refreshObjects(newObjects);
                }
            }
        });
    }

    public List<PinObject> getSelectedObjects() {
        List<PinObject> selectedObjects = new ArrayList<>();
        for (PinMultiSelect.MultiSelectObject selectObject : adapter.selectObjects) {
            Object value = selectObject.value();
            if (value instanceof String string) {
                selectedObjects.add(new PinString(string));
            } else if (value instanceof Integer integer) {
                selectedObjects.add(new PinInteger(integer));
            }
        }
        return selectedObjects;
    }
}
