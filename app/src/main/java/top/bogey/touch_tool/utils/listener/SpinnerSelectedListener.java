package top.bogey.touch_tool.utils.listener;

import android.view.View;
import android.widget.AdapterView;

public abstract class SpinnerSelectedListener implements AdapterView.OnItemSelectedListener {
    @Override
    public abstract void onItemSelected(AdapterView<?> parent, View view, int position, long id);

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}
