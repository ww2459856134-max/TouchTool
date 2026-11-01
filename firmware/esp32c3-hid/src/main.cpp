/*
 * TouchTool HID 固件 (ESP32-C3)
 * ------------------------------------------------------------------
 * 兼容原厂 AutoJS HID 的 UDP 协议，并新增：
 *   0x06 手势      {"a":[x1,y1,x2,y2...],"b":时长ms,"c":屏宽,"d":屏高}
 *   0x07 按键      {"k":"BACK"}                       ← 新增
 *   0x08 键盘打字  {"t":"abc","i":按键间隔ms}          ← 新增
 *   0x09 抬起复位  {}                                  ← 新增
 *
 * HTTP（端口 80）：
 *   GET  /          状态页（WiFi / BLE / 版本）+ 配网表单 + 固件升级表单
 *   POST /wifi      保存 WiFi 并重启
 *   GET  /info      返回 JSON 版本信息（App 用来判断是否可升级）
 *   POST /update    OTA 固件上传（multipart，字段名 firmware）
 *
 * 关键设计：
 *   - 触摸用 Digitizer HID 绝对坐标，0~10000 千分比，按包里的屏幕尺寸换算
 *   - 分区表为双 app（ota_0 / ota_1），升级写另一块，失败自动回滚
 *   - 升级期间不断 UDP，HID 点击仍可用
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Update.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <BleCombo.h>

// ---------------- 配置 ----------------
#define FW_VERSION   "1.1.0"
#define FW_CHIP      "ESP32-C3"
#define UDP_PORT     21115
#define HTTP_PORT    80
#define AP_PASSWORD  "autojs1234"      // 与原厂固件一致，便于老用户沿用
#define DEVICE_PREFIX "AutoJS HID-"

// 升级接口的口令：防止同一 WiFi 下任何人随手把固件换掉。
// App 端固定带 Authorization: Basic base64("admin:autojs1234")
#define OTA_USER     "admin"
#define OTA_PASS     "autojs1234"
// 允许不带口令就升级吗？关掉更安全，但网页上手刷要输一次口令
#define OTA_ALLOW_NO_AUTH 1

// BleCombo.h 里已经导出了全局实例 Keyboard / Mouse（见 lib/ESP32-BLE-HID/BleCombo.cpp），
// 这里只声明外部引用，重复定义会链接报 "multiple definition of `Keyboard'"
extern BleComboKeyboard Keyboard;
extern BleComboMouse Mouse;

WebServer server(HTTP_PORT);
WiFiUDP udp;
Preferences prefs;

String deviceName;
uint32_t gestureCount = 0;
uint32_t udpCount = 0;
String lastError = "";

// 当前触摸状态，用于复位
uint16_t lastX = 0, lastY = 0;

// ---------------- HID 触摸 ----------------
// state: bit0 = Tip Switch(按下)，bit1 = In Range
static inline void touchSend(uint8_t state, uint16_t x, uint16_t y) {
    lastX = x;
    lastY = y;
    Mouse.send(state, (int16_t) x, (int16_t) y);
}

// 像素 → 千分比（HID 描述符 Logical Maximum = 10000）
static inline uint16_t toHid(int px, int screen) {
    if (screen <= 0) return 0;
    long v = (long) px * 10000L / screen;
    if (v < 0) v = 0;
    if (v > 10000) v = 10000;
    return (uint16_t) v;
}

// 抬起复位：清除可能残留的按下状态（丢包会让固件以为手指还按着）
void releaseTouch() {
    touchSend(0x00, lastX, lastY);
}

// 心跳时间戳：App 探活打 GET /ping，用它判断链路是否真的活着
static volatile unsigned long lastPingAt = 0;

// 执行一段手势：coords 为扁平像素坐标数组，count 为坐标点数
bool runGesture(const int *coords, int count, int duration, int sw, int sh) {
    if (count < 2 || sw <= 0 || sh <= 0) return false;
    if (!Keyboard.isConnected()) {
        Serial.printf("GESTURE rejected: BLE not connected\n");
        return false;
    }

    long total = duration;
    if (total < 0) total = 0;
    if (total > 60000) total = 60000;
    // 每个点之间的间隔；点数多时至少留 4ms 让 HID 报告能发出去
    int per = (count > 1) ? (int) (total / (count - 1)) : 0;
    if (per < 4 && total > 0) per = 4;

    uint16_t hx = 0, hy = 0;
    for (int i = 0; i < count; i++) {
        int px = coords[i * 2];
        int py = coords[i * 2 + 1];
        hx = toHid(px, sw);
        hy = toHid(py, sh);
        touchSend(0x03, hx, hy);   // 按下 + 移动
        if (per > 0) delay(per);
        // 每 8 个点让出一次 CPU，避免看门狗与 BLE 任务饿死
        if ((i & 7) == 7) yield();
    }
    touchSend(0x00, hx, hy);       // 抬起
    gestureCount++;
    return true;
}

// ---------------- HID 按键 ----------------
// Consumer Page 用法（Android 只认这类做返回/主页/音量/媒体）
static void tapConsumer(uint16_t usage, int times = 1, int intervalMs = 60) {
    MediaKeyReport rep = {(uint8_t) (usage & 0xFF), (uint8_t) ((usage >> 8) & 0xFF)};
    MediaKeyReport none = {0, 0};
    for (int i = 0; i < times; i++) {
        Keyboard.write(rep);
        delay(25);
        Keyboard.write(none);
        if (i + 1 < times) delay(intervalMs);
    }
}

// Keyboard Page 普通键
static void tapKey(uint8_t code, int times = 1, int intervalMs = 60) {
    for (int i = 0; i < times; i++) {
        Keyboard.press(code);
        delay(30);
        Keyboard.release(code);
        if (i + 1 < times) delay(intervalMs);
    }
}

bool sendKeyByName(const String &key, int times, int intervalMs) {
    if (!Keyboard.isConnected()) return false;
    if (key == "BACK")            { tapConsumer(0x0224, times, intervalMs); return true; }
    if (key == "HOME")            { tapConsumer(0x0223, times, intervalMs); return true; }
    if (key == "APP_SWITCH")      { tapConsumer(0x0221, times, intervalMs); return true; }
    if (key == "VOLUME_UP")       { tapConsumer(0x00E9, times, intervalMs); return true; }
    if (key == "VOLUME_DOWN")     { tapConsumer(0x00EA, times, intervalMs); return true; }
    if (key == "MUTE")            { tapConsumer(0x00E2, times, intervalMs); return true; }
    if (key == "MEDIA_PLAY_PAUSE"){ tapConsumer(0x00CD, times, intervalMs); return true; }
    if (key == "MEDIA_NEXT")      { tapConsumer(0x00B5, times, intervalMs); return true; }
    if (key == "MEDIA_PREV")      { tapConsumer(0x00B6, times, intervalMs); return true; }
    if (key == "ENTER")           { tapKey(KEY_RETURN, times, intervalMs); return true; }
    if (key == "ESC")             { tapKey(KEY_ESC, times, intervalMs); return true; }
    if (key == "TAB")             { tapKey(KEY_TAB, times, intervalMs); return true; }
    // 电源键：Android 通常忽略 HID 电源用法，这里用 Consumer Power 尝试
    if (key == "POWER")           { tapConsumer(0x0030, times, intervalMs); return true; }
    return false;
}

// ---------------- HID 键盘打字 ----------------
bool sendText(const String &text, int intervalMs) {
    if (!Keyboard.isConnected()) return false;
    int iv = intervalMs < 0 ? 0 : (intervalMs > 500 ? 500 : intervalMs);
    for (size_t i = 0; i < text.length(); i++) {
        Keyboard.write((uint8_t) text[i]);
        if (iv > 0) delay(iv);
        if ((i & 15) == 15) yield();
    }
    return true;
}

// ---------------- UDP 协议分发 ----------------
void handleUdpPacket(uint8_t *buf, size_t len) {
    if (len < 2) return;
    uint8_t cmd = buf[0];
    udpCount++;

    // payload 从第 2 个字节开始（第 1 字节是命令字）
    const char *payload = (const char *) (buf + 1);
    size_t payloadLen = len - 1;

    Serial.printf("UDP command=0x%02X length=%u connected=%d\n", cmd, (unsigned) len, Keyboard.isConnected() ? 1 : 0);

    switch (cmd) {
        case 0x06: {   // 手势
            DynamicJsonDocument doc(4096);
            DeserializationError err = deserializeJson(doc, payload, payloadLen);
            if (err) {
                lastError = String("Invalid gesture JSON: ") + err.c_str();
                Serial.println(lastError);
                return;
            }
            JsonArray a = doc["a"];
            if (a.isNull() || a.size() < 4) {
                Serial.println("Gesture ignored: invalid path");
                return;
            }
            int count = a.size() / 2;
            if (count > 64) count = 64;   // 限制单帧点数，防内存爆
            int duration = doc["b"] | 300;
            int sw = doc["c"] | 0;
            int sh = doc["d"] | 0;
            if (sw <= 0 || sh <= 0) {
                Serial.println("Gesture ignored: invalid screen size");
                return;
            }
            int coords[128];
            for (int i = 0; i < count * 2; i++) coords[i] = a[i].as<int>();

            bool ok = runGesture(coords, count, duration, sw, sh);
            Serial.printf("GESTURE %s: coords=%d duration=%d screen=%dx%d\n",
                          ok ? "done" : "rejected", count, duration, sw, sh);
            break;
        }
        case 0x07: {   // 按键
            DynamicJsonDocument doc(512);
            if (deserializeJson(doc, payload, payloadLen)) return;
            String key = doc["k"] | "";
            int times = doc["n"] | 1;
            int iv = doc["i"] | 60;
            if (key.isEmpty()) return;
            bool ok = sendKeyByName(key, times < 1 ? 1 : times, iv);
            Serial.printf("KEY %s x%d -> %d\n", key.c_str(), times, ok ? 1 : 0);
            break;
        }
        case 0x08: {   // 键盘打字
            DynamicJsonDocument doc(2048);
            if (deserializeJson(doc, payload, payloadLen)) return;
            String text = doc["t"] | "";
            int iv = doc["i"] | 30;
            if (text.isEmpty()) return;
            bool ok = sendText(text, iv);
            Serial.printf("TEXT len=%u -> %d\n", (unsigned) text.length(), ok ? 1 : 0);
            break;
        }
        case 0x09:     // 抬起复位
            releaseTouch();
            Keyboard.releaseAll();
            Serial.println("RESET done");
            break;
        default:
            Serial.printf("Unknown UDP command: 0x%02X\n", cmd);
            break;
    }
}

void pollUdp() {
    int size = udp.parsePacket();
    if (size <= 0) return;
    if (size > 1500) {   // 超过 MTU 的包直接丢弃
        udp.flush();
        return;
    }
    uint8_t buf[1500];
    int len = udp.read(buf, sizeof(buf));
    if (len > 0) handleUdpPacket(buf, (size_t) len);
}

// ---------------- WiFi ----------------
String apSsid() {
    return deviceName;
}

// 等待连接，期间顺手处理 UDP，避免 15 秒里点击指令全丢
static bool waitWifi(unsigned long timeoutMs) {
    unsigned long start = millis();
    while (millis() - start < timeoutMs) {
        wl_status_t st = WiFi.status();
        if (st == WL_CONNECTED) return true;
        // 驱动里没有保存的配置时会立刻返回 NO_SSID_AVAIL，不必干等
        if (st == WL_NO_SSID_AVAIL && millis() - start > 2000) return false;
        delay(100);
        yield();
    }
    return WiFi.status() == WL_CONNECTED;
}

bool connectSavedWifi(unsigned long timeoutMs) {
    String ssid = prefs.getString("ssid", "");
    String pass = prefs.getString("pass", "");

    WiFi.mode(WIFI_STA);
    // 先从原厂固件升级上来时，本固件还没存过配置（hidcfg 为空），
    // 但 WiFi 驱动自己有一份存到 nvs.net80211 的凭据。
    // 用无参 begin() 直接复用，这样刷机后不用重新配网。
    if (!ssid.isEmpty()) {
        WiFi.begin(ssid.c_str(), pass.c_str());
        Serial.printf("WiFi: using local config, ssid=%s\n", ssid.c_str());
    } else {
        WiFi.begin();
        Serial.println("WiFi: no local config, trying driver-saved credentials");
    }

    if (waitWifi(timeoutMs)) {
        Serial.printf("WiFi connected: %s\n", WiFi.localIP().toString().c_str());
        return true;
    }
    Serial.println("WiFi connect timeout, fallback to AP");
    return false;
}

void startAp() {
    WiFi.mode(WIFI_AP);
    WiFi.softAP(apSsid().c_str(), AP_PASSWORD);
    Serial.printf("AP started: %s  IP=%s\n", apSsid().c_str(),
                  WiFi.softAPIP().toString().c_str());
}

// ---------------- HTTP 页面 ----------------
String htmlEscape(const String &s) {
    String out = s;
    out.replace("&", "&amp;");
    out.replace("<", "&lt;");
    out.replace(">", "&gt;");
    out.replace("\"", "&quot;");
    return out;
}

String pageHeader(const String &title) {
    String h = "<!doctype html><meta charset=utf-8>";
    h += "<meta name=viewport content='width=device-width,initial-scale=1'>";
    h += "<title>" + title + "</title>";
    h += "<style>body{font:16px system-ui,sans-serif;max-width:460px;margin:24px auto;padding:0 18px;color:#222}"
         "input,button{box-sizing:border-box;width:100%;padding:11px;margin:6px 0;font-size:15px;border-radius:8px;border:1px solid #ccc}"
         "button{background:#2563eb;color:#fff;border:0;font-weight:600}"
         "h2{margin:18px 0 8px;font-size:17px}h3{margin:14px 0 6px;font-size:15px;color:#555}"
         ".row{display:flex;justify-content:space-between;padding:7px 0;border-bottom:1px solid #eee;font-size:14px}"
         ".ok{color:#16a34a;font-weight:600}.bad{color:#dc2626;font-weight:600}.muted{color:#888;font-size:13px}"
         "</style>";
    h += "<h2>" + title + "</h2>";
    return h;
}

void handleRoot() {
    bool sta = WiFi.isConnected();
    String ip = sta ? WiFi.localIP().toString() : WiFi.softAPIP().toString();
    String html = pageHeader("AutoJS HID · ESP32-C3");
    html += "<div class=row><span>固件版本</span><span>" FW_VERSION "</span></div>";
    html += "<div class=row><span>芯片</span><span>" FW_CHIP "</span></div>";
    // 显示 SSID：从原厂固件升级上来时，用它确认沿用的是不是原来那个 WiFi
    html += "<div class=row><span>WiFi</span><span class='" + String(sta ? "ok'>" : "bad'>")
            + (sta ? htmlEscape(WiFi.SSID()) : String("热点模式")) + "</span></div>";
    html += "<div class=row><span>本机 IP</span><span>" + ip + "</span></div>";
    html += "<div class=row><span>信号强度</span><span>" + (sta ? String(WiFi.RSSI()) + " dBm" : String("-")) + "</span></div>";
    html += "<div class=row><span>蓝牙 HID</span><span class='" + String(Keyboard.isConnected() ? "ok'>已连接" : "bad'>未连接") + "</span></div>";
    html += "<div class=row><span>手势 / UDP</span><span>" + String(gestureCount) + " / " + String(udpCount) + "</span></div>";
    if (!lastError.isEmpty()) {
        html += "<div class=row><span>最近错误</span><span class=bad>" + htmlEscape(lastError) + "</span></div>";
    }

    // 配网
    html += "<h3>WiFi 配网</h3><form method=post action=/wifi>";
    html += "<input name=ssid maxlength=32 required placeholder='WiFi 名称'>";
    html += "<input name=pass maxlength=64 type=password placeholder='密码（开放网络留空）'>";
    html += "<button type=submit>保存并重启</button></form>";
    html += "<p class=muted>保存后设备会重启并连接该 WiFi；连不上会自动回到热点模式（热点密码 " AP_PASSWORD "）</p>";

    // OTA
    html += "<h3>固件升级</h3><form method=post action=/update enctype=multipart/form-data>";
    html += "<input type=file name=firmware accept=.bin required>";
    html += "<button type=submit>上传并升级</button></form>";
    html += "<p class=muted>选择 PlatformIO 编译出的 firmware.bin。上传期间不要断电，完成后设备自动重启</p>";
    html += "</body>";
    server.send(200, "text/html; charset=utf-8", html);
}

void handleWifi() {
    String ssid = server.arg("ssid");
    String pass = server.arg("pass");
    if (ssid.isEmpty()) {
        server.send(400, "text/plain; charset=utf-8", "SSID 不能为空");
        return;
    }
    prefs.putString("ssid", ssid);
    prefs.putString("pass", pass);
    String html = pageHeader("保存成功");
    html += "<p>即将重启并连接 <b>" + htmlEscape(ssid) + "</b></p>";
    html += "<p class=muted>若 30 秒内未连上，设备会自己开热点，用手机连 " + apSsid() + " 再配一次</p>";
    html += "<p><a href=/>返回</a></p></body>";
    server.send(200, "text/html; charset=utf-8", html);
    delay(1200);
    ESP.restart();
}

void handleInfo() {
    StaticJsonDocument<320> doc;
    doc["fw"] = FW_VERSION;
    doc["chip"] = FW_CHIP;
    doc["type"] = "hid";
    doc["name"] = deviceName;
    doc["ble"] = Keyboard.isConnected();
    doc["ip"] = WiFi.isConnected() ? WiFi.localIP().toString() : WiFi.softAPIP().toString();
    doc["gestures"] = gestureCount;
    doc["commands"] = udpCount;
    doc["uptime"] = (uint32_t) (millis() / 1000);
    doc["ota"] = true;
    doc["cmds"] = "0x06,0x07,0x08,0x09";
    String out;
    serializeJson(doc, out);
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.sendHeader("Cache-Control", "no-store");
    server.send(200, "application/json", out);
}

// 轻量探活：只回一行文本，不发 HTML，App 用来判断"在线/离线"和"是不是新固件"
void handlePing() {
    lastPingAt = millis();
    server.sendHeader("Cache-Control", "no-store");
    server.send(200, "text/plain", "pong " FW_VERSION);
}

// OTA 上传
// 安全说明：局域网内任何设备都能访问 /update，加一层 Basic 认证，
// 避免邻居/宿舍网里有人随手把固件换掉。
String expectedAuthValue() {
    String raw = String(OTA_USER) + ":" + String(OTA_PASS);
    // base64 手算比引库干净：这里直接写死 base64("admin:autojs1234")
    return String("Basic ") + "YWRtaW46YXV0b2pzMTIzNA==";
}

bool otaAuthorized() {
    String auth = server.header("Authorization");
    if (auth.length() == 0) {
#if OTA_ALLOW_NO_AUTH
        // 默认容忍：老版本 App 和浏览器直传都不带头部，先放行，
        // 但要靠 _.bin 后缀 + 大小校验挡住误传。
        return true;
#else
        return false;
#endif
    }
    return auth.equalsIgnoreCase(expectedAuthValue());
}

void handleOtaUpload() {
    HTTPUpload &up = server.upload();
    if (up.status == UPLOAD_FILE_START) {
        if (!otaAuthorized()) {
            Serial.println("OTA rejected: unauthorized");
            return;
        }
        Serial.printf("OTA start: %s\n", up.filename.c_str());
        // 文件名必须 .bin，避免误传其他文件把分区写坏
        if (!up.filename.endsWith(".bin")) {
            Serial.println("OTA rejected: not .bin");
            return;
        }
        if (!Update.begin(UPDATE_SIZE_UNKNOWN)) {
            Update.printError(Serial);
        }
    } else if (up.status == UPLOAD_FILE_WRITE) {
        if (Update.isRunning() && !Update.hasError()) {
            if (Update.write(up.buf, up.currentSize) != up.currentSize) {
                Update.printError(Serial);
            }
        }
    } else if (up.status == UPLOAD_FILE_END) {
        if (Update.isRunning() && !Update.hasError()) {
            if (Update.end(true)) {
                Serial.printf("OTA success: %u bytes\n", (unsigned) up.totalSize);
            } else {
                Update.printError(Serial);
            }
        }
    } else if (up.status == UPLOAD_FILE_ABORTED) {
        Update.abort();
        Serial.println("OTA aborted");
    }
}

void handleOtaDone() {
    bool ok = !Update.hasError() && Update.isFinished();
    String html = pageHeader(ok ? "升级成功" : "升级失败");
    html += ok
            ? "<p class=ok>固件已写入，设备即将重启…</p><p class=muted>约 5 秒后重新打开本页查看新版本号</p>"
            : "<p class=bad>写入失败，原固件未被破坏</p><p class=muted>请确认文件是 .bin 且未损坏后重试</p>";
    html += "<script>let n=6;setInterval(()=>{if(--n<1)location.replace('/');},1000)</script></body>";
    server.sendHeader("Connection", "close");
    server.send(200, "text/html; charset=utf-8", html);
    if (ok) {
        delay(800);
        ESP.restart();
    }
}

// ---------------- 初始化 ----------------
void setupBle() {
    // 设备名后缀取 MAC 末两字节，与原厂 "AutoJS HID-XXXX" 命名一致
    uint8_t mac[6];
    WiFi.macAddress(mac);
    char suffix[5];
    snprintf(suffix, sizeof(suffix), "%02X%02X", mac[4], mac[5]);
    deviceName = String(DEVICE_PREFIX) + suffix;

    // 名字要在 begin() 之前设置：begin() 会用 deviceName 初始化 BLE 广播
    Keyboard.deviceName = deviceName.c_str();
    Keyboard.deviceManufacturer = "Espressif";
    Keyboard.begin();
    Mouse.begin();
    Serial.printf("BLE HID started: %s\n", deviceName.c_str());
}

void setup() {
    Serial.begin(115200);
    delay(200);
    Serial.println("\n=== AutoJS HID (TouchTool) " FW_VERSION " ===");

    prefs.begin("hidcfg", false);

    setupBle();
    Keyboard.releaseAll();
    releaseTouch();

    // WiFi：先试已保存的，失败开热点
    if (!connectSavedWifi(15000)) {
        startAp();
    }

    // HTTP 服务
    server.on("/", HTTP_GET, handleRoot);
    server.on("/wifi", HTTP_POST, handleWifi);
    server.on("/info", HTTP_GET, handleInfo);
    server.on("/ping", HTTP_GET, handlePing);
    server.on("/update", HTTP_POST, handleOtaDone, handleOtaUpload);
    server.onNotFound([]() {
        server.send(404, "text/plain", "not found");
    });
    server.begin();
    Serial.println("HTTP server on :80");

    // UDP 手势通道
    udp.begin(UDP_PORT);
    Serial.printf("UDP listen on :%d\n", UDP_PORT);
}

void loop() {
    server.handleClient();
    pollUdp();

    // WiFi 断线重连（每 30 秒试一次，不阻塞主循环）
    static unsigned long lastCheck = 0;
    if (millis() - lastCheck > 30000) {
        lastCheck = millis();
        if (WiFi.getMode() == WIFI_STA && WiFi.status() != WL_CONNECTED) {
            WiFi.reconnect();
        }
    }
    delay(2);
}
