package top.bogey.touch_tool.ui;

import android.app.Activity;
import android.content.Intent;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.service.MainAccessibilityService;

public class PermissionActivity extends BaseActivity {
    public static final String CAPTURE_PERMISSION = "CAPTURE_PERMISSION";

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        boolean capture = intent.getBooleanExtra(CAPTURE_PERMISSION, false);
        if (capture) {
            MainAccessibilityService service = MainApplication.getInstance().getService();
            if (service == null || !service.isEnabled()) {
                finish();
                return;
            }

            launchNotification((notifyCode, notifyIntent) -> {
                if (notifyCode == Activity.RESULT_OK) {
                    launchCapture((code, data) -> {
                        service.bindCapture(code == Activity.RESULT_OK, data);
                        finish();
                    });
                } else {
                    service.bindCapture(false, null);
                    finish();
                }
            });
        }
    }
}
