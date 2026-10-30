package top.bogey.touch_tool.utils;

import android.graphics.Point;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

import top.bogey.touch_tool.MainApplication;

// AutoJS HID（ESP32）UDP 客户端，供 HID 中继、点击图片等动作共用
// 协议（抓官方 App 包实锤）：UDP 到设备 21115 端口，载荷 = 0x06 + JSON
// {"a":[x1,y1,x2,y2,...],"b":时长ms,"c":屏宽,"d":屏高}
// a 为扁平坐标数组（按下/抬起同一点即为单击），无结束符
public class HidClient {

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

        // UDP 偶发丢包，失败自动重发一次
        for (int attempt = 0; attempt < 2; attempt++) {
            try (DatagramSocket socket = new DatagramSocket()) {
                byte[] jsonBytes = json.getBytes();
                byte[] payload = new byte[1 + jsonBytes.length];
                payload[0] = 0x06;
                System.arraycopy(jsonBytes, 0, payload, 1, jsonBytes.length);
                if (hexLog != null) {
                    StringBuilder hex = new StringBuilder();
                    for (byte b : payload) hex.append(String.format("%02X ", b));
                    hexLog.append(hex);
                }
                DatagramPacket packet = new DatagramPacket(payload, payload.length,
                        InetAddress.getByName(host.trim()), port);
                socket.send(packet);
                return true;
            } catch (Exception e) {
                if (hexLog != null) hexLog.append("发送失败: ").append(e);
            }
        }
        return false;
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
                String body = httpGet(ip, 80, 500);
                if (body != null && (body.contains("ESP32") || body.contains("esp32") || body.contains("AutoJS"))) {
                    found.add(ip);
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

    /** 取本机 WiFi 局域网 IP */
    public static String getLocalIp() {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en =
                 java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                java.net.NetworkInterface nic = en.nextElement();
                if (!nic.isUp() || nic.isLoopback()) continue;
                for (java.net.InterfaceAddress addr : nic.getInterfaceAddresses()) {
                    java.net.InetAddress ia = addr.getAddress();
                    if (ia instanceof java.net.Inet4Address && !ia.isLoopbackAddress()) {
                        String ip = ia.getHostAddress();
                        // 跳过热点网段 192.168.4.x 与蜂窝网段
                        if (ip.startsWith("192.168.4.") || ip.startsWith("10.")) continue;
                        return ip;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
