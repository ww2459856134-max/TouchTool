package top.bogey.touch_tool.ui.tool.task_manager;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import top.bogey.touch_tool.utils.GsonUtil;

// 任务分享码：把任务记录压缩编码成一段可复制粘贴的文本码，粘贴即可导入
//
// 格式：TT1.<Base64URL>(GZIP(JSON))
// 版本号前缀便于后续格式升级时兼容识别；Base64 用 URL-SAFE 变体，可安全经过微信/QQ 等渠道传输
public class TaskShareCode {
    public static final String PREFIX = "TT1.";

    // 任务记录转分享码，失败返回 null
    public static String encode(TaskRecord record) {
        try {
            String json = GsonUtil.toJson(record);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return PREFIX + Base64.encodeToString(bytes.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception e) {
            return null;
        }
    }

    // 分享码转任务记录，格式不对返回 null
    public static TaskRecord decode(String code) {
        if (code == null) return null;
        code = code.trim();
        if (!code.startsWith(PREFIX)) return null;
        try {
            byte[] bytes = Base64.decode(code.substring(PREFIX.length()), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            StringBuilder json = new StringBuilder();
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = gzip.read(buffer)) > 0) {
                    json.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                }
            }
            return GsonUtil.getAsObject(json.toString(), TaskRecord.class, null);
        } catch (Exception e) {
            return null;
        }
    }
}
