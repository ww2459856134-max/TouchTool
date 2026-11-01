package top.bogey.touch_tool.utils;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import top.bogey.touch_tool.MainApplication;

// ESP32 HID 固件的 OTA 升级客户端
// 与固件约定：
//   GET  /info     → {"fw":"1.1.0","chip":"ESP32-C3",...}
//   POST /update   → multipart/form-data，字段名 firmware，文件名需 .bin
// 升级期间设备仍在跑 UDP 手势，写完自动重启并切到新分区
public class HidOtaClient {

    /** base64("admin:autojs1234")，与固件 OTA_USER/OTA_PASS 保持一致 */
    private static final String AUTH_B64 = "YWRtaW46YXV0b2pzMTIzNA==";

    public interface Progress {
        void onProgress(int percent, long sent, long total);

        void onResult(boolean ok, String message);
    }

    /** 读取设备信息（版本、芯片、BLE 状态），失败返回 null */
    public static String fetchInfo(String host, int port, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://" + host.trim() + ":" + port + "/info").openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && sb.length() < 4096) sb.append(line);
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 通过 ContentResolver 查文件真实大小，查不到返回 -1 */
    private static long queryFileSize(ContentResolver resolver, Uri uri) {
        android.database.Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[]{
                    android.provider.OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1;
    }

    /**
     * 上传固件并升级
     *
     * @param host  设备 IP
     * @param port  HTTP 端口（80）
     * @param uri   本地 .bin 文件
     */
    public static void uploadFirmware(String host, int port, Uri uri, String fileName, Progress progress) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                ContentResolver resolver = MainApplication.getInstance().getContentResolver();
                long total = queryFileSize(resolver, uri);

                String boundary = "----TouchTool" + System.currentTimeMillis();
                String safeName = fileName == null || !fileName.toLowerCase().endsWith(".bin")
                        ? "firmware.bin" : fileName;

                String head = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"firmware\"; filename=\"" + safeName + "\"\r\n"
                        + "Content-Type: application/octet-stream\r\n\r\n";
                String tail = "\r\n--" + boundary + "--\r\n";
                byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
                byte[] tailBytes = tail.getBytes(StandardCharsets.UTF_8);

                conn = (HttpURLConnection) new URL("http://" + host.trim() + ":" + port + "/update").openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(180000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("Connection", "close");
                // 固件端 /update 接受 Basic 认证（用户名 admin，密码与热点密码一致）
                conn.setRequestProperty("Authorization", "Basic " + AUTH_B64);
                // ESP32 的 WebServer 不解析 chunked 请求体。必须给出确定长度，
                // 否则 Content-Length 缺失，服务器读到 0 字节就结束，升级一定失败。
                if (total <= 0) {
                    progress.onResult(false, "读不到文件大小，请先从文件管理器把 .bin 复制到手机存储再选");
                    return;
                }
                conn.setFixedLengthStreamingMode(headBytes.length + total + tailBytes.length);

                try (OutputStream out = conn.getOutputStream();
                     InputStream in = resolver.openInputStream(uri)) {
                    if (in == null) {
                        progress.onResult(false, "无法读取所选文件");
                        return;
                    }
                    out.write(headBytes);
                    byte[] buf = new byte[8192];
                    long sent = 0;
                    int len;
                    int lastPercent = -1;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                        sent += len;
                        int percent = (int) (sent * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            progress.onProgress(percent, sent, total);
                        }
                    }
                    out.write(tailBytes);
                    out.flush();
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    progress.onResult(true, "升级完成，设备正在重启");
                } else {
                    progress.onResult(false, "设备返回 HTTP " + code);
                }
            } catch (Exception e) {
                progress.onResult(false, "上传失败：" + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "hid-ota").start();
    }
}
