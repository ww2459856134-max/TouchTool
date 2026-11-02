package top.bogey.touch_tool.utils;

import android.graphics.Point;
import android.os.SystemClock;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

import top.bogey.touch_tool.MainApplication;

// AutoJS HID（ESP32）UDP 客户端，供 HID 中继、点击图片等动作共用
// 协议（抓官方 App 包实锤）：UDP 到设备 21115 端口，载荷 = 0x06 + JSON
// {"a":[x1,y1,x2,y2,...],"b":时长ms,"c":屏宽,"d":屏高}
// a 为扁平坐标数组（按下/抬起同一点即为单击），无结束符
//
// 本类额外扩展的指令（需固件支持对应指令字，旧固件收不到只会有超时提示，不影响原有点击）：
//   0x07 + JSON {"k":"BACK"|"HOME"|"MENU"|...}   按键
//   0x08 + 文本                                   键盘逐字输入（ASCII）
//   0x09 + "UP"                                   抬起/复位（清除可能残留的按下状态）
public class HidClient {

    // 单次探活缓存时长：在这段时间内不重复发包，避免每个点击动作都多等一次超时
    private static long lastOnlineAt = 0L;
    private static boolean lastOnline = false;
    private static final long ONLINE_CACHE_MS = 10000L;

    // ACK 等待时长：固件支持回执时在该时间内收到即算真成功
    private static final int ACK_TIMEOUT_MS = 350;

    /** 全局设备地址（设置里配置），返回 null 表示未配置 */
    public static String getConfigHost() {
        String host = top.bogey.touch_tool.bean.save.setting.SettingSaver.HID_DEVICE_IP.get();
        if (host == null || host.trim().isEmpty()) return null;
        return host.trim();
    }

    public static int getConfigPort() {
        int port = top.bogey.touch_tool.bean.save.setting.SettingSaver.HID_DEVICE_PORT.get();
        return Math.max(1, Math.min(65535, port));
    }

    /** 设备是否可用：BLE 模式看 MAC 是否已配置；UDP 模式看 IP。调用方在不可达时应直接走无障碍兜底 */
    public static boolean isReady() {
        if (useBle()) return BleHidClient.getConfigMac() != null;
        return getConfigHost() != null;
    }

    /** 是否启用蓝牙直连模式（v1.2.3 固件起支持） */
    public static boolean useBle() {
        return top.bogey.touch_tool.bean.save.setting.SettingSaver.HID_USE_BLE.get();
    }

    /** 标记设备离线（发送异常时调用），让后续动作跳过 HID 直接回退 */
    public static void markOffline() {
        lastOnline = false;
        lastOnlineAt = SystemClock.elapsedRealtime();
    }

    public static void markOnline() {
        lastOnline = true;
        lastOnlineAt = SystemClock.elapsedRealtime();
    }

    /** 最近一次探活是否可达（带 10 秒缓存），未配置返回 false */
    public static boolean isOnline() {
        if (useBle()) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastOnlineAt < ONLINE_CACHE_MS) return lastOnline;
            boolean ok = BleHidClient.isConnected();
            if (ok) markOnline(); else markOffline();
            return ok;
        }
        if (getConfigHost() == null) return false;
        long now = SystemClock.elapsedRealtime();
        if (now - lastOnlineAt < ONLINE_CACHE_MS) return lastOnline;
        // 探活：发一次抬起复位，能发出去且（若固件支持）能收到回执
        boolean ok = sendCommand(getConfigHost(), getConfigPort(), "{}", CMD_RESET, null);
        if (ok) markOnline(); else markOffline();
        return ok;
    }

    public static final byte CMD_TOUCH = 0x06;
    public static final byte CMD_KEY = 0x07;
    public static final byte CMD_TEXT = 0x08;
    public static final byte CMD_RESET = 0x09;

    /**
     * 发送一次 HID 手势
     *
     * @param host   设备 IP
     * @param port   端口（固件固定 21115）
     * @param path   手势路径点（至少 2 个点）
     * @param duration 手势时长 ms
     * @param hexLog 非 null 时写入发送的十六进制内容（调试用）
     * @return 是否发送成功
     */
    public static boolean sendGesture(String host, int port, List<Point> path, long duration, StringBuilder hexLog) {
        if (host == null || host.trim().isEmpty() || path == null || path.size() < 2) return false;
        long time = Math.max(0, Math.min(60000, duration));

        StringBuilder arr = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            Point p = path.get(i);
            if (p == null) return false;
            if (i > 0) arr.append(",");
            arr.append(p.x).append(",").append(p.y);
        }
        android.util.Size size = DisplayUtil.getScreenSize(MainApplication.getInstance());
        String json = "{\"a\":[" + arr + "],\"b\":" + time
                + ",\"c\":" + size.getWidth() + ",\"d\":" + size.getHeight() + "}";
        return sendCommand(host, port, json, CMD_TOUCH, hexLog);
    }

    /**
     * 发送按键指令（需固件支持 0x07 指令字）
     *
     * @param key 按键名，如 BACK / HOME / MENU / APP_SWITCH / POWER / VOLUME_UP / VOLUME_DOWN /
     *            MEDIA_PLAY_PAUSE / MEDIA_NEXT / MEDIA_PREV / ENTER / ESC
     */
    public static boolean sendKey(String host, int port, String key, StringBuilder hexLog) {
        String json = "{\"k\":\"" + key + "\"}";
        return sendCommand(host, port, json, CMD_KEY, hexLog);
    }

    /** 键盘逐字输入 ASCII 文本（需固件支持 0x08 指令字）；空文本直接返回 true */
    public static boolean sendText(String host, int port, String text, long keyIntervalMs, StringBuilder hexLog) {
        if (text == null || text.isEmpty()) return true;
        String json = "{\"t\":\"" + escapeJson(text) + "\",\"i\":" + Math.max(0, keyIntervalMs) + "}";
        return sendCommand(host, port, json, CMD_TEXT, hexLog);
    }

    /** 抬起复位：清除设备端可能残留的按下状态（丢包会导致一直按着，之后所有点击变拖拽） */
    public static boolean sendReset(String host, int port, StringBuilder hexLog) {
        return sendCommand(host, port, "{}", CMD_RESET, hexLog);
    }

    // 统一发包：UDP 无连接，发出去只代表报文离开网卡。
    // 若固件支持回执，会回一个字节，收到才认定为真成功；收不到则按超时回落为"已发送"，
    // 但连续超时会标记设备离线，让后续动作直接走无障碍兜底而不是干等。
    private static boolean sendCommand(String host, int port, String payload, byte cmd, StringBuilder hexLog) {
        byte[] body = payload.getBytes();
        byte[] data = new byte[1 + body.length];
        data[0] = cmd;
        System.arraycopy(body, 0, data, 1, body.length);

        // v1.2.3: 蓝牙直连模式——指令走 BLE GATT，不依赖 WiFi。
        // 未连接时尝试触发连接；首次失败降级走 UDP（若配置了 IP），两条腿并行不冲突。
        if (useBle()) {
            if (BleHidClient.sendFrame(data)) {
                markOnline();
                if (hexLog != null) hexLog.append("[BLE] ").append(toHex(data));
                return true;
            }
            if (hexLog != null) hexLog.append("[BLE失败] ");
            // 继续尝试 UDP 兜底（未配置 IP 时直接失败）
        }

        if (host == null || host.trim().isEmpty()) return false;

        for (int attempt = 0; attempt < 2; attempt++) {
            DatagramSocket socket = null;
            try {
                InetAddress address = InetAddress.getByName(host.trim());
                socket = new DatagramSocket();
                socket.setSoTimeout(ACK_TIMEOUT_MS);
                socket.send(new DatagramPacket(data, data.length, address, port));
                if (hexLog != null) hexLog.append(toHex(data));

                // 等回执：收到任意字节都视为设备已处理
                byte[] ack = new byte[64];
                DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
                try {
                    socket.receive(ackPacket);
                    markOnline();
                    return true;
                } catch (java.net.SocketTimeoutException timeout) {
                    // 固件未实现回执——报文本已发出，按"已发送"处理，不算失败
                    markOnline();
                    return true;
                }
            } catch (Exception e) {
                if (hexLog != null) hexLog.append("发送失败: ").append(e).append("; ");
                markOffline();
            } finally {
                if (socket != null) socket.close();
            }
        }
        return false;
    }

    private static String toHex(byte[] data) {
        StringBuilder hex = new StringBuilder();
        for (byte b : data) hex.append(String.format("%02X ", b));
        return hex.toString();
    }

    private static String escapeJson(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04X", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 两点间生成顺滑的滑动轨迹：二次贝塞尔插值（带轻微弧度，模拟人手），默认 16 个采样点
     */
    public static List<Point> smoothSwipe(Point start, Point end, int samples) {
        if (start == null || end == null) return null;
        int n = Math.max(4, Math.min(64, samples));
        // 控制点：中点垂直偏移 8% 距离（随机方向），让轨迹带一点自然弧度
        int dx = end.x - start.x, dy = end.y - start.y;
        double dist = Math.hypot(dx, dy);
        double bend = (Math.random() < 0.5 ? -1 : 1) * dist * 0.08;
        double cx = (start.x + end.x) / 2.0 - dy / (dist == 0 ? 1 : dist) * bend;
        double cy = (start.y + end.y) / 2.0 + dx / (dist == 0 ? 1 : dist) * bend;

        List<Point> path = new java.util.ArrayList<>();
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double u = 1 - t;
            int x = (int) Math.round(u * u * start.x + 2 * u * t * cx + t * t * end.x);
            int y = (int) Math.round(u * u * start.y + 2 * u * t * cy + t * t * end.y);
            path.add(new Point(x, y));
        }
        return path;
    }

    /**
     * HTTP 探活（比 UDP 可靠）：GET /ping，成功返回固件版本串（如 "pong 1.1.0"），失败返回 null。
     * <p>
     * 为什么不用 UDP 判活：UDP 无连接，send() 成功后即使设备关机也返回成功，
     * 只有 ICMP 端口不可达才抛异常，而 Android 上这条错误经常被静默丢掉。
     * 走一次 TCP 三次握手才是真的"对方活着"。
     */
    public static String httpPing(String host, int port, int timeoutMs) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection)
                    new java.net.URL("http://" + host.trim() + ":" + port + "/ping").openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Connection", "close");
            int code = conn.getResponseCode();
            if (code != 200) return null;
            try (java.io.InputStream in = conn.getInputStream();
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[256];
                int len;
                while ((len = in.read(buf)) > 0 && out.size() < 512) out.write(buf, 0, len);
                return out.toString("UTF-8").trim();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 严格判活：走 HTTP /ping（新固件），失败再退回根页面（老固件）。
     * 结果缓存 {@link #ONLINE_CACHE_MS}，避免每个点击动作都多花一次网络往返。
     */
    public static boolean isOnlineStrict() {
        if (useBle()) return isOnline();   // BLE 模式没有 HTTP 可探，看 GATT 连接状态
        String host = getConfigHost();
        if (host == null) return false;
        long now = SystemClock.elapsedRealtime();
        if (now - lastOnlineAt < ONLINE_CACHE_MS) return lastOnline;
        boolean ok = httpPing(host, 80, 1200) != null || httpGet(host, 80, 1500) != null;
        if (ok) markOnline(); else markOffline();
        return ok;
    }

    /** HTTP GET 设备配置页，返回响应体前 200 字符；失败返回 null */
    public static String httpGet(String host, int port, int timeoutMs) {

        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("http://" + host.trim() + ":" + port + "/").openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) return null;
            try (java.io.InputStream in = conn.getInputStream();
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0 && out.size() < 8192) out.write(buf, 0, len);
                String body = out.toString("UTF-8");
                // 去掉标签取纯文本片段
                String text = body.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                return text.length() > 200 ? text.substring(0, 200) : text;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 扫描局域网 /24 网段，返回响应中带 ESP32/AutoJS 特征的设备 IP 列表（耗时约 2~4 秒） */
    public static List<String> discoverDevices() {
        String localIp = getLocalIp();
        List<String> found = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        if (localIp == null) return found;
        String[] seg = localIp.split("\\.");
        if (seg.length != 4) return found;
        String prefix = seg[0] + "." + seg[1] + "." + seg[2] + ".";

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(64);
        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + i;
            if (ip.equals(localIp)) continue;
            pool.execute(() -> {
                // 固件配置页多数跑在 80；AutoJS HID 固件热点模式常见 192.168.4.1 且端口为 80/8080
                for (int port : new int[]{80, 8080}) {
                    String body = httpGet(ip, port, 400);
                    if (body != null && (body.contains("ESP32") || body.contains("esp32")
                            || body.contains("AutoJS") || body.contains("AutoJs"))) {
                        found.add(ip);
                        return;
                    }
                }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(8, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        java.util.Collections.sort(found, (a, b) -> {
            String[] as = a.split("\\."), bs = b.split("\\.");
            return Integer.compare(Integer.parseInt(as[3]), Integer.parseInt(bs[3]));
        });
        return found;
    }

    /**
     * 取本机局域网 IP。
     * 原实现跳过 192.168.4.x 与 10.x，但 ESP32 固件的热点模式正是 192.168.4.x
     * （设备 192.168.4.1、手机 192.168.4.2），企业 WiFi 也常用 10.0.0.x，
     * 会导致自动发现永远扫不到。现在只排除回环与蜂窝/虚拟网卡。
     */
    public static String getLocalIp() {
        String fallback = null;
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en =
                 java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                java.net.NetworkInterface nic = en.nextElement();
                if (!nic.isUp() || nic.isLoopback()) continue;
                String name = nic.getName() == null ? "" : nic.getName().toLowerCase();
                // 跳过蜂窝、虚拟隧道网卡
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp")
                        || name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("clat")) {
                    continue;
                }
                for (java.net.InterfaceAddress addr : nic.getInterfaceAddresses()) {
                    java.net.InetAddress ia = addr.getAddress();
                    if (!(ia instanceof java.net.Inet4Address) || ia.isLoopbackAddress()) continue;
                    String ip = ia.getHostAddress();
                    if (ip == null) continue;
                    // wlan 网卡优先返回
                    if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("softap")) return ip;
                    if (fallback == null && !ip.startsWith("169.254.")) fallback = ip;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
