package top.bogey.touch_tool.utils.float_window_manager;

import android.view.MotionEvent;

public interface FloatCallback {
    void onCreate();

    void onShow(String tag);

    void onHide();

    void onDismiss();

    void onDrag();

    void onDragEnd();

    void onRotate();

    boolean onTouch(MotionEvent event);
}
