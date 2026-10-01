package top.bogey.touch_tool.utils.callback;

import android.content.Intent;

public interface ActivityResultCallback {
    void onResult(int code, Intent intent);
}
