package top.bogey.touch_tool.bean.action.system;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.JsonObject;

import java.io.File;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

// 安装 APK：唤起系统安装界面安装指定 APK 文件（需用户授予"安装未知应用"权限）
public class InstallApkAction extends ExecuteAction {
    private final transient Pin pathPin = new Pin(new PinString(), R.string.download_file_action_saved_path);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public InstallApkAction() {
        super(ActionType.INSTALL_APK);
        addPins(pathPin, successPin, elsePin);
    }

    public InstallApkAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, successPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String path = getPinValue(runnable, pathPin).toString();
        successPin.getValue(PinBoolean.class).setValue(false);

        File file = new File(path.trim());
        // 常见笔误兜底：误填了 .part 临时文件时找同名正式文件
        if (!file.exists() && path.endsWith(".part")) {
            File real = new File(path.substring(0, path.length() - 5));
            if (real.exists()) file = real;
        }

        if (!file.exists() || !file.getName().toLowerCase().endsWith(".apk")) {
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        MainApplication application = MainApplication.getInstance();
        // 未授予"安装未知应用"权限时跳转设置页，引导用户开一次
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !application.getPackageManager().canRequestPackageInstalls()) {
            AppUtil.runOnUiThread(() -> Toast.makeText(application, R.string.install_apk_need_permission, Toast.LENGTH_LONG).show());
            Intent settingIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + application.getPackageName()));
            settingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            application.startActivity(settingIntent);
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(application, application.getPackageName() + ".file_provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            application.startActivity(intent);
            successPin.getValue(PinBoolean.class).setValue(true);
            executeNext(runnable, outPin);
        } catch (Exception e) {
            markUnachieved();
            executeNext(runnable, elsePin);
        }
    }
}
