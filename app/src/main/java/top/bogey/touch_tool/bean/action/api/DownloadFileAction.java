package top.bogey.touch_tool.bean.action.api;

import android.os.Environment;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinLong;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskRunnable;

// 下载文件：从 URL 下载文件保存到本地，输出成功状态、保存路径与文件大小
// 先写 .part 临时文件，完成后改名——保证"成功"输出的文件一定是完整的
public class DownloadFileAction extends ExecuteAction {
    private final transient Pin urlPin = new Pin(new PinString(), R.string.http_request_action_url);
    private final transient Pin pathPin = new Pin(new PinString(), R.string.download_file_action_path);
    private final transient Pin conflictPin = new NotLinkAblePin(new PinSingleSelect(R.array.download_conflict), R.string.download_file_action_conflict, false, false, true);
    private final transient Pin timeoutPin = new NotLinkAblePin(new PinInteger(30000), R.string.http_request_action_timeout, false, false, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin savedPathPin = new Pin(new PinString(), R.string.download_file_action_saved_path, true);
    private final transient Pin sizePin = new Pin(new PinLong(0), R.string.download_file_action_size, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.if_action_else, true);

    public DownloadFileAction() {
        super(ActionType.DOWNLOAD_FILE);
        addPins(urlPin, pathPin, conflictPin, timeoutPin, successPin, savedPathPin, sizePin, elsePin);
    }

    public DownloadFileAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(urlPin, pathPin, conflictPin, timeoutPin, successPin, savedPathPin, sizePin, elsePin);
        // 高级参数固定归入第二页
        conflictPin.setHide(true);
        timeoutPin.setHide(true);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String url = getPinValue(runnable, urlPin).toString();
        String path = getPinValue(runnable, pathPin).toString();
        int conflict = 0;
        if (getPinValue(runnable, conflictPin) instanceof PinSingleSelect select) conflict = select.getIndex();
        int timeout = 30000;
        if (getPinValue(runnable, timeoutPin) instanceof PinInteger pinInteger) {
            timeout = Math.max(1000, pinInteger.getValue());
        }

        successPin.getValue(PinBoolean.class).setValue(false);
        savedPathPin.setValue(new PinString(""));
        sizePin.getValue(PinLong.class).setValue(0L);

        if (url.isEmpty()) {
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        HttpURLConnection connection = null;
        try {
            // 手动跟随跳转（含 http→https 跨协议，系统默认不跟），最多 5 层
            String currentUrl = url;
            int code = 0;
            for (int redirect = 0; redirect < 5; redirect++) {
                if (connection != null) connection.disconnect();
                connection = (HttpURLConnection) new URL(currentUrl).openConnection();
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);
                connection.setInstanceFollowRedirects(false);
                // 默认用浏览器 UA，避免被服务器当程序拒绝
                if (connection.getRequestProperty("User-Agent") == null) {
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                }
                code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) break;
                    currentUrl = new URL(new URL(currentUrl), location).toString();
                    continue;
                }
                break;
            }

            if (code < 200 || code >= 300) {
                markUnachieved();
                executeNext(runnable, elsePin);
                return;
            }

            // 文件名按跳转后的最终地址推断（短链解析出的才是真实文件名）
            File target = resolveTargetFile(currentUrl, path);
            // 跳过策略：文件已存在直接成功返回，不重复下载
            if (conflict == 2 && target.exists() && target.length() > 0) {
                savedPathPin.setValue(new PinString(target.getAbsolutePath()));
                sizePin.getValue(PinLong.class).setValue(target.length());
                successPin.getValue(PinBoolean.class).setValue(true);
                executeNext(runnable, outPin);
                return;
            }
            // 自动重命名：file.png → file(1).png
            if (conflict == 1) target = renameIfExists(target);

            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            // 先写临时文件，完成后再改名
            File part = new File(target.getAbsolutePath() + ".part");

            long size = 0;
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    size += read;
                }
            }
            // 下载完成后改名；目标被占用时先删旧文件
            if (target.exists()) target.delete();
            if (!part.renameTo(target)) throw new Exception("rename failed");

            savedPathPin.setValue(new PinString(target.getAbsolutePath()));
            sizePin.getValue(PinLong.class).setValue(size);
            successPin.getValue(PinBoolean.class).setValue(true);
            executeNext(runnable, outPin);
        } catch (Exception e) {
            markUnachieved();
            executeNext(runnable, elsePin);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private File renameIfExists(File file) {
        if (!file.exists()) return file;
        String name = file.getName();
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        File parent = file.getParentFile();
        for (int i = 1; i < 1000; i++) {
            File candidate = new File(parent, base + "(" + i + ")" + ext);
            if (!candidate.exists()) return candidate;
        }
        return file;
    }

    // 路径为空时存到系统下载目录/点击助手/URL文件名；路径是目录时在目录下存 URL 文件名
    private File resolveTargetFile(String url, String path) {
        String fileName = "file_" + System.currentTimeMillis();
        try {
            String clean = url.split("\\?")[0];
            String name = clean.substring(clean.lastIndexOf('/') + 1);
            if (!name.isEmpty()) fileName = java.net.URLDecoder.decode(name, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
        }

        if (path == null || path.trim().isEmpty()) {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            return new File(new File(dir, "点击助手"), fileName);
        }
        // 以 / 结尾视为目录（哪怕还不存在）；已存在的目录同样往里存
        if (path.trim().endsWith("/") || path.trim().endsWith("\\")) {
            return new File(new File(path.trim()), fileName);
        }
        File file = new File(path.trim());
        if (file.isDirectory()) return new File(file, fileName);
        return file;
    }
}
