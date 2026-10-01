package top.bogey.touch_tool.bean.action.api;

import android.graphics.Bitmap;
import android.os.Build;
import android.widget.Toast;

import com.google.gson.JsonObject;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinCodeString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

// JS 代码运行：使用 Rhino 引擎执行 JavaScript 代码
// 内置能力：input 输入变量、console.log 日志捕获、toast/sleep/httpGet/httpPost 工具函数、执行超时保护
public class JsCodeAction extends ExecuteAction {
    private final transient Pin codePin = new Pin(new PinCodeString(), R.string.js_code_action_code);
    private final transient Pin inputPin = new Pin(new PinString(), R.string.js_code_action_input);
    private final transient Pin maxTimePin = new NotLinkAblePin(new PinInteger(10000), R.string.js_code_action_max_time, false, false, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin resultPin = new Pin(new PinString(), R.string.js_code_action_result, true);
    private final transient Pin logsPin = new Pin(new PinString(), R.string.js_code_action_logs, true);
    private final transient Pin errorPin = new Pin(new PinString(), R.string.js_code_action_error, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public JsCodeAction() {
        super(ActionType.JS_CODE);
        addPins(codePin, inputPin, maxTimePin, successPin, resultPin, logsPin, errorPin, elsePin);
    }

    public JsCodeAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(codePin, inputPin, maxTimePin, successPin, resultPin, logsPin, errorPin, elsePin);
        // 高级参数固定归入第二页
        maxTimePin.setHide(true);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String code = getPinValue(runnable, codePin).toString();
        String input = getPinValue(runnable, inputPin).toString();
        long maxTime = 10000;
        if (getPinValue(runnable, maxTimePin) instanceof PinInteger pinInteger) {
            maxTime = Math.max(1000, Math.min(120000, pinInteger.getValue()));
        }

        successPin.getValue(PinBoolean.class).setValue(false);
        resultPin.setValue(new PinString(""));
        logsPin.setValue(new PinString(""));
        errorPin.setValue(new PinString(""));

        if (code.isEmpty()) {
            executeNext(runnable, elsePin);
            return;
        }

        StringBuilder logs = new StringBuilder();
        Context context = null;
        try {
            context = Context.enter(new TimeoutContext(System.currentTimeMillis() + maxTime));
            // Android 无法使用字节码生成，必须使用解释模式；解释模式支持指令观察器(超时保护)
            context.setOptimizationLevel(-1);
            context.setInstructionObserverThreshold(2000);
            Scriptable scope = context.initStandardObjects();

            // 注入 input 变量：能解析为 JSON 时作为对象，否则作为原始字符串
            if (!input.isEmpty()) {
                String inputJs;
                try {
                    new org.json.JSONTokener(input).nextValue();
                    inputJs = "(" + input + ")";
                } catch (Exception e) {
                    inputJs = "'" + input.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "") + "'";
                }
                context.evaluateString(scope, "var input = " + inputJs + ";", "input", 1, null);
            }

            // console.log/info/warn/error 捕获到日志输出
            Scriptable consoleObj = context.newObject(scope);
            BaseFunction logFunction = new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope1, Scriptable thisObj, Object[] args) {
                    if (logs.length() > 0) logs.append("\n");
                    logs.append(joinArgs(args));
                    return Undefined.instance;
                }
            };
            consoleObj.put("log", consoleObj, logFunction);
            consoleObj.put("info", consoleObj, logFunction);
            consoleObj.put("warn", consoleObj, logFunction);
            consoleObj.put("error", consoleObj, logFunction);
            ScriptableObject.putProperty(scope, "console", consoleObj);

            // toast(msg)：显示吐司
            ScriptableObject.putProperty(scope, "toast", new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope1, Scriptable thisObj, Object[] args) {
                    String msg = joinArgs(args);
                    AppUtil.runOnUiThread(() -> Toast.makeText(MainApplication.getInstance(), msg, Toast.LENGTH_SHORT).show());
                    return Undefined.instance;
                }
            });

            // sleep(ms)：等待
            ScriptableObject.putProperty(scope, "sleep", new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope1, Scriptable thisObj, Object[] args) {
                    long ms = 0;
                    if (args.length > 0) ms = (long) Context.toNumber(args[0]);
                    if (ms > 0) runnable.sleep(ms);
                    return Undefined.instance;
                }
            });

            // httpGet(url)：发起 GET 请求返回响应体
            ScriptableObject.putProperty(scope, "httpGet", new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope1, Scriptable thisObj, Object[] args) {
                    String url = args.length > 0 ? Context.toString(args[0]) : "";
                    return httpRequest("GET", url, null);
                }
            });

            // httpPost(url, body)：发起 POST 请求返回响应体
            ScriptableObject.putProperty(scope, "httpPost", new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope1, Scriptable thisObj, Object[] args) {
                    String url = args.length > 0 ? Context.toString(args[0]) : "";
                    String body = args.length > 1 ? Context.toString(args[1]) : "";
                    return httpRequest("POST", url, body);
                }
            });

            Object result = context.evaluateString(scope, code, "JsCodeAction", 1, null);
            String value = result == null || result == Undefined.instance ? "" : Context.toString(result);
            resultPin.setValue(new PinString(value));
            logsPin.setValue(new PinString(logs.toString()));
            successPin.getValue(PinBoolean.class).setValue(true);
            executeNext(runnable, outPin);
        } catch (RhinoException e) {
            logsPin.setValue(new PinString(logs.toString()));
            errorPin.setValue(new PinString("第" + e.lineNumber() + "行: " + e.getMessage()));
            executeNext(runnable, elsePin);
        } catch (Exception e) {
            logsPin.setValue(new PinString(logs.toString()));
            errorPin.setValue(new PinString(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            executeNext(runnable, elsePin);
        } finally {
            if (context != null) Context.exit();
        }
    }

    private String joinArgs(Object[] args) {
        StringBuilder builder = new StringBuilder();
        for (Object arg : args) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(arg == null ? "null" : Context.toString(arg));
        }
        return builder.toString();
    }

    // JS 内置 HTTP 请求
    private String httpRequest(String method, String url, String body) {
        if (url == null || url.isEmpty()) throw new EvaluatorException("URL 不能为空", "JsCodeAction", 1);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            if (body != null && !body.isEmpty()) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
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
            return builder.toString();
        } catch (EvaluatorException e) {
            throw e;
        } catch (Exception e) {
            throw new EvaluatorException("请求失败: " + e.getMessage(), "JsCodeAction", 1);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // 超时保护上下文：解释模式下定期回调，超时抛出异常中断脚本
    private static class TimeoutContext extends Context {
        private final long deadline;

        public TimeoutContext(long deadline) {
            this.deadline = deadline;
        }

        @Override
        public void observeInstructionCount(int instructionCount) {
            if (System.currentTimeMillis() > deadline) {
                throw new EvaluatorException("脚本执行超时", "JsCodeAction", 1);
            }
        }
    }
}
