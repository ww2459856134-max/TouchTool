package top.bogey.touch_tool.bean.action.string;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.save.setting.SettingSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

// 云端 OCR：百度智能云通用文字识别（含文字坐标），密钥在设置页配置
public class CloudOcrAction extends ExecuteAction {
    private final transient Pin sourcePin = new Pin(new PinImage(), R.string.pin_image_full, false, false, true);
    private final transient Pin areaPin = new Pin(new PinArea(), R.string.find_image_action_search_area);
    private final transient Pin textPin = new Pin(new PinString(), R.string.cloud_ocr_text, true);
    private final transient Pin textsPin = new Pin(new PinList(new PinString()), R.string.cloud_ocr_texts, true);
    private final transient Pin areasPin = new Pin(new PinList(new PinArea()), R.string.cloud_ocr_areas, true);
    private final transient Pin errorPin = new Pin(new PinString(), R.string.cloud_ocr_error, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    private static String accessToken;
    private static long tokenExpireTime;

    public CloudOcrAction() {
        super(ActionType.CLOUD_OCR_TEXT);
        addPins(sourcePin, areaPin, textPin, textsPin, areasPin, errorPin, successPin, elsePin);
    }

    public CloudOcrAction(com.google.gson.JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(sourcePin, areaPin, textPin, textsPin, areasPin, errorPin, successPin, elsePin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String apiKey = SettingSaver.BAIDU_OCR_API_KEY.get();
        String secretKey = SettingSaver.BAIDU_OCR_SECRET_KEY.get();
        if (apiKey == null || apiKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            errorPin.getValue(PinString.class).setValue("未配置云端OCR密钥，请在设置中填写");
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        Bitmap bitmap;
        if (sourcePin.isLinked()) {
            PinImage source = getPinValue(runnable, sourcePin);
            bitmap = source.getImage();
        } else {
            MainAccessibilityService service = MainApplication.getInstance().getService();
            bitmap = service == null ? null : service.tryGetScreenShot();
        }
        if (bitmap == null) {
            errorPin.getValue(PinString.class).setValue("no image");
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        // 区域裁剪
        Rect area = ((PinArea) getPinValue(runnable, areaPin)).getValue();
        if (area != null && !area.isEmpty()) {
            int left = Math.max(0, area.left);
            int top = Math.max(0, area.top);
            int width = Math.min(bitmap.getWidth() - left, area.width());
            int height = Math.min(bitmap.getHeight() - top, area.height());
            if (width > 0 && height > 0) {
                Bitmap crop = Bitmap.createBitmap(bitmap, left, top, width, height);
                if (crop != bitmap) bitmap = crop;
            }
        }

        String response = requestOcr(bitmap, apiKey, secretKey);
        if (response == null) {
            errorPin.getValue(PinString.class).setValue("network error");
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        try {
            JsonObject jsonObject = com.google.gson.JsonParser.parseString(response).getAsJsonObject();
            if (jsonObject.has("error_code")) {
                String error = "error " + jsonObject.get("error_code").getAsString() + ": " +
                        (jsonObject.has("error_msg") ? jsonObject.get("error_msg").getAsString() : "unknown");
                errorPin.getValue(PinString.class).setValue(error);
                markUnachieved();
                executeNext(runnable, elsePin);
                return;
            }

            JsonArray wordsResult = jsonObject.getAsJsonArray("words_result");
            PinList textList = textsPin.getValue(PinList.class);
            PinList areaList = areasPin.getValue(PinList.class);
            StringBuilder builder = new StringBuilder();
            if (wordsResult != null) {
                for (JsonElement element : wordsResult) {
                    JsonObject item = element.getAsJsonObject();
                    String words = item.has("words") ? item.get("words").getAsString() : "";
                    builder.append(words).append("\n");
                    textList.add(new PinString(words));
                    if (item.has("location")) {
                        JsonObject location = item.getAsJsonObject("location");
                        Rect rect = new Rect(
                                location.get("left").getAsInt(),
                                location.get("top").getAsInt(),
                                location.get("left").getAsInt() + location.get("width").getAsInt(),
                                location.get("top").getAsInt() + location.get("height").getAsInt());
                        areaList.add(new PinArea(rect));
                    }
                }
            }
            textPin.getValue(PinString.class).setValue(builder.toString().trim());
            successPin.getValue(PinBoolean.class).setValue(true);
            executeNext(runnable, outPin);
        } catch (Exception e) {
            errorPin.getValue(PinString.class).setValue(e.toString());
            markUnachieved();
            executeNext(runnable, elsePin);
        }
    }

    private String requestOcr(Bitmap bitmap, String apiKey, String secretKey) {
        String token = getAccessToken(apiKey, secretKey);
        if (token == null) return null;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
        String base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);

        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic?access_token=" + token);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);
            String body = "image=" + URLEncoder.encode(base64, StandardCharsets.UTF_8.name());
            try (java.io.OutputStream stream = connection.getOutputStream()) {
                stream.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private synchronized String getAccessToken(String apiKey, String secretKey) {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) return accessToken;
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=" +
                    URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name()) +
                    "&client_secret=" + URLEncoder.encode(secretKey, StandardCharsets.UTF_8.name()));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            JsonObject jsonObject = com.google.gson.JsonParser.parseString(builder.toString()).getAsJsonObject();
            if (jsonObject.has("access_token")) {
                accessToken = jsonObject.get("access_token").getAsString();
                tokenExpireTime = System.currentTimeMillis() + (jsonObject.has("expires_in") ? jsonObject.get("expires_in").getAsLong() * 1000L : 86400000L) - 60000L;
                return accessToken;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }
}
