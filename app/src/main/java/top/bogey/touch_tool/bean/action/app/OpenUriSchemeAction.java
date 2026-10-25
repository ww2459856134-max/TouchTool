package top.bogey.touch_tool.bean.action.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

import java.util.List;

public class OpenUriSchemeAction extends ExecuteAction {
    // 打开方式：0 系统默认，1 强制浏览器，2 指定 App
    public static final int MODE_DEFAULT = 0;
    public static final int MODE_BROWSER = 1;
    public static final int MODE_APP = 2;

    // 常用链接预设：索引 0 为"自定义链接"（用链接针脚的内容），其余按 PRESET_SCHEMES 顺序一一对应
    // 带 {占位符} 的模板：选中后填进链接框，把 {xxx} 换成真实值即可（可用"试打开"验证）
    private static final String[] PRESET_SCHEMES = {
            "weixin://",
            "weixin://scanqrcode",
            "alipay://",
            "alipays://platformapi/startapp?appId=20000056",
            "alipays://platformapi/startapp?appId=10000007",
            "taobao://",
            "openapp.jdmobile://",
            "pinduopindo://",
            "xhsdiscover://",
            "snssdk1128://",
            "intent:#Intent;action=android.settings.WIFI_SETTINGS;end",
            "intent:#Intent;action=android.settings.APPLICATION_DETAILS_SETTINGS;end",
            "tel:",
            "smsto:",
            // 短视频深链模板
            "snssdk1128://user/profile/{用户ID}",
            "snssdk1128://search/trending?keyword={关键词}",
            "kwai://",
            "kwai://search?keyword={关键词}",
            "xhsdiscover://user/{用户ID}",
            "xhsdiscover://search/result?keyword={关键词}",
            // 系统设置直达页
            "intent:#Intent;action=android.settings.BLUETOOTH_SETTINGS;end",
            "intent:#Intent;action=android.settings.NOTIFICATION_SETTINGS;end",
            "intent:#Intent;action=android.settings.APPLICATION_DEVELOPMENT_SETTINGS;end",
            "intent:#Intent;action=android.settings.APPLICATION_DETAILS_SETTINGS;data=package:com.tencent.mm;end",
            "intent:#Intent;action=android.settings.MANAGE_APP_PERMISSIONS;data=package:com.tencent.mm;end",
            "intent:#Intent;action=android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS;end",
            "intent:#Intent;action=android.settings.ACCESSIBILITY_SETTINGS;end"
    };

    private final transient Pin uriPin = new Pin(new PinString(), R.string.open_uri_scheme_action_uri);
    // 打开方式与 App 针脚追加在末尾，旧任务按位置同步不受影响
    private final transient Pin modePin = new Pin(new PinSingleSelect(R.array.open_uri_scheme_mode, MODE_DEFAULT), R.string.open_uri_scheme_action_mode);
    // 指定用哪个 App 打开：仅"指定 App"模式显示
    private final transient Pin appPin = new AppShowAblePin(new PinApplication(PinSubType.SINGLE_APP), R.string.pin_app);
    // 常用链接预设：下拉选择后自动把对应 DeepLink 填进链接针脚
    private final transient Pin presetPin = new NotLinkAblePin(new PinSingleSelect(R.array.open_uri_scheme_preset, 0), R.string.open_uri_scheme_action_preset);

    public OpenUriSchemeAction() {
        super(ActionType.OPEN_URI_SCHEME);
        addPins(uriPin, modePin, appPin, presetPin);
    }

    public OpenUriSchemeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(uriPin, modePin, appPin, presetPin);
    }

    // 读取当前打开方式
    public int mode() {
        PinSingleSelect mode = modePin.getValue(PinSingleSelect.class);
        return mode != null ? mode.getIndex() : MODE_DEFAULT;
    }

    // 读取当前预设（0 = 自定义链接）
    public int preset() {
        PinSingleSelect preset = presetPin.getValue(PinSingleSelect.class);
        return preset != null ? preset.getIndex() : 0;
    }

    // 按预设序号取 DeepLink；越界或自定义返回 null
    private String getPresetScheme(int index) {
        if (index <= 0 || index > PRESET_SCHEMES.length) return null;
        return PRESET_SCHEMES[index - 1];
    }

    // 卡片上选择常用链接后，自动把对应 DeepLink 填进链接针脚（所见即所得，还能在其基础上改）
    @Override
    public void onValueUpdated(Task task, Pin origin, PinBase value) {
        super.onValueUpdated(task, origin, value);
        if (origin == presetPin && value instanceof PinSingleSelect select) {
            String scheme = getPresetScheme(select.getIndex());
            if (scheme != null) uriPin.setValue(task, new PinString(scheme));
        }
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String scheme;
        // 选了预设：以预设链接为准；选"自定义链接"：用链接针脚的内容（可连线动态传入）
        if (preset() > 0) {
            scheme = getPresetScheme(preset());
        } else {
            PinString uri = getPinValue(runnable, uriPin);
            scheme = uri == null ? null : uri.getValue();
        }
        if (scheme != null && !scheme.trim().isEmpty()) {
            scheme = scheme.trim();
            switch (mode()) {
                case MODE_BROWSER -> openWithBrowser(scheme);
                case MODE_APP -> openWithApp(runnable, scheme);
                default -> AppUtil.gotoScheme(MainApplication.getInstance().getService(), scheme);
            }
        }
        executeNext(runnable, outPin);
    }

    // 强制浏览器打开：在所有能处理该链接的应用里挑浏览器（带 BROWSER 分类的优先，包名含 browser 兜底）
    private void openWithBrowser(String scheme) {
        try {
            Intent intent = Intent.parseUri(scheme, 0);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setComponent(null);
            if (intent.getData() == null) {
                AppUtil.startActivity(MainApplication.getInstance().getService(), intent, null);
                return;
            }
            Intent query = new Intent(Intent.ACTION_VIEW, intent.getData());
            List<ResolveInfo> apps = MainApplication.getInstance().getService().getPackageManager().queryIntentActivities(query, PackageManager.MATCH_ALL);
            String browser = null;
            String fallback = null;
            for (ResolveInfo info : apps) {
                if (info.activityInfo == null) continue;
                if (info.filter != null && info.filter.hasCategory(Intent.CATEGORY_APP_BROWSER)) {
                    browser = info.activityInfo.packageName;
                    break;
                }
                String pkg = info.activityInfo.packageName;
                if (fallback == null && pkg != null && (pkg.contains("browser") || pkg.contains("webview"))) {
                    fallback = pkg;
                }
            }
            if (browser == null) browser = fallback;
            // 没找到浏览器（比如是 DeepLink 只有目标 App 能处理）：回落系统默认方式打开
            if (browser != null) intent.setPackage(browser);
            AppUtil.startActivity(MainApplication.getInstance().getService(), intent, null);
        } catch (Exception e) {
            AppUtil.gotoScheme(MainApplication.getInstance().getService(), scheme);
        }
    }

    // 指定 App 打开：链接里指定的 component 清掉，强制走选定包名
    private void openWithApp(TaskRunnable runnable, String scheme) {
        PinApplication app = getPinValue(runnable, appPin);
        openWithApp(scheme, app == null ? null : app.getPackageName());
    }

    private void openWithApp(String scheme, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            AppUtil.gotoScheme(MainApplication.getInstance().getService(), scheme);
            return;
        }
        try {
            Intent intent = Intent.parseUri(scheme, 0);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setComponent(null);
            intent.setPackage(packageName);
            AppUtil.startActivity(MainApplication.getInstance().getService(), intent, null);
        } catch (Exception e) {
            // 指定 App 无法处理该链接时，回落系统默认打开，保证任务不中断
            AppUtil.gotoScheme(MainApplication.getInstance().getService(), scheme);
        }
    }

    // 试打开：不跑任务，直接按卡片当前配置打开链接（预设/链接框、打开方式、指定 App 全部生效）
    public void testRun() {
        String scheme;
        if (preset() > 0) {
            scheme = getPresetScheme(preset());
        } else {
            PinString uri = uriPin.getValue(PinString.class);
            scheme = uri == null ? null : uri.getValue();
        }
        Context context = MainApplication.getInstance().getService();
        if (scheme == null || scheme.trim().isEmpty()) {
            Toast.makeText(context, R.string.open_uri_scheme_action_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        scheme = scheme.trim();
        switch (mode()) {
            case MODE_BROWSER -> openWithBrowser(scheme);
            case MODE_APP -> {
                PinApplication app = appPin.getValue(PinApplication.class);
                openWithApp(scheme, app == null ? null : app.getPackageName());
            }
            default -> AppUtil.gotoScheme(context, scheme);
        }
        Toast.makeText(context, R.string.open_uri_scheme_action_test, Toast.LENGTH_SHORT).show();
    }

    // 只在"指定 App"模式显示 App 针脚
    private static class AppShowAblePin extends ShowAblePin {
        public AppShowAblePin(PinApplication value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            if (context.getAction(getOwnerId()) instanceof OpenUriSchemeAction action) {
                return action.mode() == MODE_APP;
            }
            return true;
        }
    }
}
