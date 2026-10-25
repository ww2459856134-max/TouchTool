package top.bogey.touch_tool.bean.action.api;

import android.os.Build;

import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionCheckResult;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.action.system.SwitchCaptureAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;

// HTTP 请求：向指定 URL 发起请求，输出状态码与响应体
public class HttpRequestAction extends ExecuteAction {
    private final transient Pin urlPin = new Pin(new PinString(), R.string.http_request_action_url);
    private final transient Pin methodPin = new NotLinkAblePin(new PinSingleSelect(R.array.http_method), R.string.http_request_action_method);
    private final transient Pin headersPin = new Pin(new PinList(new PinString()), R.string.http_request_action_headers, false, false, true);
    private final transient Pin bodyPin = new Pin(new PinString(), R.string.http_request_action_body, false, false, true);
    private final transient Pin timeoutPin = new NotLinkAblePin(new PinInteger(10000), R.string.http_request_action_timeout, false, false, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin statusCodePin = new Pin(new PinInteger(0), R.string.http_request_action_status, true);
    private final transient Pin responsePin = new Pin(new PinString(), R.string.http_request_action_response, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public HttpRequestAction() {
        super(ActionType.HTTP_REQUEST);
        addPins(urlPin, methodPin, headersPin, bodyPin, timeoutPin, successPin, statusCodePin, responsePin, elsePin);
    }

    public HttpRequestAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(urlPin, methodPin, headersPin, bodyPin, timeoutPin, successPin, statusCodePin, responsePin, elsePin);
        // 高级参数固定归入第二页
        headersPin.setHide(true);
        bodyPin.setHide(true);
        timeoutPin.setHide(true);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String url = getPinValue(runnable, urlPin).toString();
        PinSingleSelect method = getPinValue(runnable, methodPin);
        PinList headers = getPinValue(runnable, headersPin);
        String body = getPinValue(runnable, bodyPin).toString();
        int timeout = 10000;
        if (getPinValue(runnable, timeoutPin) instanceof PinInteger pinInteger) {
            timeout = Math.max(1000, pinInteger.getValue());
        }

        successPin.getValue(PinBoolean.class).setValue(false);
        statusCodePin.getValue(PinInteger.class).setValue(0);
        responsePin.setValue(new PinString(""));

        if (url.isEmpty()) {
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method.getIndex() == 0 ? "GET" : method.getIndex() == 1 ? "POST" : method.getIndex() == 2 ? "PUT" : "DELETE");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            // 解析"键: 值"格式的请求头
            if (headers != null) {
                for (PinObject item : headers) {
                    String line = item == null ? "" : item.toString();
                    int split = line.indexOf(':');
                    if (split > 0 && split < line.length() - 1) {
                        connection.setRequestProperty(line.substring(0, split).trim(), line.substring(split + 1).trim());
                    }
                }
            }

            boolean hasBody = (method.getIndex() == 1 || method.getIndex() == 2) && !body.isEmpty();
            if (hasBody) {
                connection.setDoOutput(true);
                if (connection.getRequestProperty("Content-Type") == null) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                }
                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = connection.getResponseCode();
            statusCodePin.getValue(PinInteger.class).setValue(code);

            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder builder = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (builder.length() > 0) builder.append('\n');
                        builder.append(line);
                    }
                }
            }
            responsePin.setValue(new PinString(builder.toString()));

            boolean success = code >= 200 && code < 300;
            successPin.getValue(PinBoolean.class).setValue(success);
            executeNext(runnable, success ? outPin : elsePin);
        } catch (Exception e) {
            markUnachieved();
            executeNext(runnable, elsePin);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Override
    public void check(ActionCheckResult result, Task task) {
        super.check(result, task);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // 低于 Android 11 的设备无法使用截屏服务时给出提示，与图片动作保持一致
        }
    }
}
