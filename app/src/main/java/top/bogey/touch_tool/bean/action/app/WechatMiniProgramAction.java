package top.bogey.touch_tool.bean.action.app;

import android.content.Intent;
import android.net.Uri;

import com.google.gson.JsonObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

// 打开微信小程序：通过微信小程序跳转协议打开指定小程序
public class WechatMiniProgramAction extends ExecuteAction {
    private final transient Pin appIdPin = new Pin(new PinString(), R.string.wechat_mini_action_app_id);
    private final transient Pin pathPin = new Pin(new PinString(), R.string.wechat_mini_action_path);
    private final transient Pin envPin = new NotLinkAblePin(new PinSingleSelect(R.array.wechat_mini_env), R.string.wechat_mini_action_env, false, false, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public WechatMiniProgramAction() {
        super(ActionType.WECHAT_MINI_PROGRAM);
        addPins(appIdPin, pathPin, envPin, successPin, elsePin);
    }

    public WechatMiniProgramAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(appIdPin, pathPin, envPin, successPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String appId = getPinValue(runnable, appIdPin).toString();
        String path = getPinValue(runnable, pathPin).toString();
        String env = "release";
        if (getPinValue(runnable, envPin) instanceof PinSingleSelect select) {
            env = switch (select.getIndex()) {
                case 1 -> "trial";
                case 2 -> "develop";
                default -> "release";
            };
        }

        successPin.getValue(PinBoolean.class).setValue(false);

        if (appId.isEmpty()) {
            executeNext(runnable, elsePin);
            return;
        }

        StringBuilder uri = new StringBuilder("weixin://dl/business/?appid=").append(appId);
        if (!path.isEmpty()) {
            uri.append("&path=").append(encode(path));
        }
        uri.append("&env_version=").append(env);

        // 微信未安装时走失败分支
        MainApplication application = MainApplication.getInstance();
        if (application.getPackageManager().getLaunchIntentForPackage("com.tencent.mm") == null) {
            executeNext(runnable, elsePin);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(uri.toString()));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AppUtil.startActivity(application.getService(), intent, null);
        successPin.getValue(PinBoolean.class).setValue(true);
        executeNext(runnable, outPin);
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
