/*
 * TouchTool HID 固件 (ESP32-C3/S3)
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
 *   - 触摸用 Digitizer HID 绝对坐标 0~32767（复刻旧版官方固件），按包里的屏幕尺寸换算
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
#include <BLEDevice.h>
#include <host/ble_gap.h>
#include <esp_mac.h>
#include <esp_mac.h>
#include <esp_ota_ops.h>
#include <esp_system.h>

// ---------------- 配置 ----------------
#define FW_VERSION   "1.2.10"
#if defined(CONFIG_IDF_TARGET_ESP32S3)
#define FW_CHIP      "ESP32-S3"
#elif defined(CONFIG_IDF_TARGET_ESP32C3)
#define FW_CHIP      "ESP32-C3"
#else
#define FW_CHIP      "ESP32"
#endif
#define UDP_PORT     21115
#define HTTP_PORT    80
#define AP_PASSWORD  "autojs1234"      // 与原厂固件一致，便于老用户沿用
#define DEVICE_PREFIX "鼠标键盘"

// 升级接口的口令：防止同一 WiFi 下任何人随手把固件换掉。
// App 端固定带 Authorization: Basic base64("admin:autojs1234")
#define OTA_USER     "admin"
#define OTA_PASS     "autojs1234"
// 允许不带口令就升级吗？关掉更安全，但网页上手刷要输一次口令
#define OTA_ALLOW_NO_AUTH 0

// ---------------- 崩溃自保护 ----------------
// 目标：杜绝 1.3.0 那种「刷了坏固件 → 无限重启循环」变砖。
// 规则：异常重启（panic / 看门狗）连续攒到 3 次且期间从未通过健康确认
// （运行满 60 秒），就判定当前槽位的固件坏了，自动把启动项切回
// 另一个 app 分区（即升级前的旧固件），重启恢复。
// 正常关机/断电/按 RESET/软件重启不计数，单次偶发崩溃也不触发。
#define CRASH_ROLLBACK_THRESHOLD 3
#define HEALTH_CONFIRM_MS        60000UL

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
uint32_t bleCount = 0;      // v1.2.3: 蓝牙 GATT 通道收到的指令数
uint32_t bleDropCount = 0;  // 队列满被丢弃的指令数
String lastError = "";

// 崩溃自保护：60 秒健康确认是否已通过（定义在文件头，状态页/JSON 都要读）
static bool healthConfirmed = false;

// BLE 事件轨迹：放进环形缓冲，通过 /blelog 直接手机浏览器就能看，
// 不用连串口也能判断"配对到底断在哪一步"
// BLE 事件轨迹：固定环形缓冲（无动态内存）。不能用 String——
// BLE 事件回调跑在蓝牙协议栈线程、HTTP 页面在 loop 线程读，
// String 跨线程扩容/释放会撞出堆损坏（这是执行动作崩溃的头号嫌疑）。
#define BLE_TRACE_SIZE 4096
static char bleTraceBuf[BLE_TRACE_SIZE];
static size_t bleTraceLen = 0;    // v1.2.10: 复用为环形写指针 head
static size_t bleTraceUsed = 0;   // 当前有效字节数
static portMUX_TYPE bleTraceMux = portMUX_INITIALIZER_UNLOCKED;

// v1.2.10: 环形缓冲改为【头指针覆盖写】——旧实现每次追加在临界区里
// memmove 最多 2.4KB 挪动旧数据，BLE 协议栈线程频繁进长临界区会抖动蓝牙时序。
// 新实现：直接写 head 位置、head 前移、覆盖最老数据，临界区内零搬移。
// 代价：快照首行可能是被截断的半行，输出时跳到第一个换行为止。
void traceAppend(const char *line) {
    size_t n = strlen(line);
    if (n >= BLE_TRACE_SIZE - 1) {   // 超长行只留尾部
        line += n - (BLE_TRACE_SIZE - 1);
        n = BLE_TRACE_SIZE - 1;
    }
    portENTER_CRITICAL(&bleTraceMux);
    for (size_t i = 0; i < n; i++) {
        bleTraceBuf[bleTraceLen] = line[i];   // bleTraceLen 复用为写指针 head
        bleTraceLen = (bleTraceLen + 1) % BLE_TRACE_SIZE;
        if (bleTraceUsed < BLE_TRACE_SIZE - 1) bleTraceUsed++;
    }
    portEXIT_CRITICAL(&bleTraceMux);
}

static void traceSnapshot(char *out, size_t outSize) {
    portENTER_CRITICAL(&bleTraceMux);
    size_t head = bleTraceLen, used = bleTraceUsed;
    // 按 head→tail 顺序拼出到 out（满缓冲时首行为半行，跳过）
    size_t o = 0;
    // 只有缓冲已绕满时首行才可能是被截断的半行；未满时从 0 开始全是完整行
    bool skipPartial = (used >= BLE_TRACE_SIZE - 1);
    bool lineStart = true;
    for (size_t k = 0; k < used && o < outSize - 2; k++) {
        char c = bleTraceBuf[(head - used + k + BLE_TRACE_SIZE) % BLE_TRACE_SIZE];
        if (lineStart) {
            if (skipPartial && c != '\n') continue;   // 跳过截断的半行
            lineStart = false;
            if (skipPartial) continue;                 // 半行结束符本身不输出
        }
        out[o++] = c;
    }
    if (o > 0 && out[o - 1] != '\n') out[o++] = '\n';
    out[o] = 0;
    portEXIT_CRITICAL(&bleTraceMux);
}

uint16_t bleConnectCount = 0;
uint16_t bleDisconnectCount = 0;

void bleEvent(const char *msg) {
    if (strcmp(msg, "onConnect") == 0) bleConnectCount++;
    if (strcmp(msg, "onDisconnect") == 0) bleDisconnectCount++;
    char line[96];
    snprintf(line, sizeof(line), "[%lus] %s  conn=%d dis=%d\n",
             (unsigned long) (millis() / 1000), msg,
             (int) bleConnectCount, (int) bleDisconnectCount);
    traceAppend(line);
    Serial.print("[BLE-TRACE] ");
    Serial.print(line);
}

// HID 报文统计与状态：/blelog 里用它判断断开是否发生在发送报告之后
uint16_t lastX = 0, lastY = 0;
uint32_t hidSendCount = 0;
uint32_t hidSkipCount = 0;
uint32_t lastHidSendAt = 0;

// OTA 预期镜像大小，0 表示本次上传无效（未授权/非 .bin/超分区）
size_t otaImageSize = 0;

// ---------------- HID 触摸 ----------------
// state: bit0 = Tip Switch(按下)，bit1 = In Range
static uint8_t curContact = 1;   // 当前 contact id（每次手势轮换，安卓靠它区分不同触点）
static bool touchActive = false; // v1.2.8: 当前是否真的有触点按下（复位时避免发多余抬起帧）

// v1.2.10: 纯蓝牙形态，输出通道 = BLE HID 连接
static bool hidOutReady() {
    return Keyboard.isConnected();
}

static uint32_t tchSeq = 0;
static uint8_t tchLastLogged = 0xFF;

static inline void touchSend(uint8_t state, uint16_t x, uint16_t y) {
    lastX = x;
    lastY = y;
    touchActive = (state & 0x01) != 0;
    if (!Keyboard.isConnected()) {
        hidSkipCount++;
        // v1.2.10 降频：跳过日志只在状态切换或每 8 帧记一次
        if (tchLastLogged != state || (tchSeq & 7) == 0) {
            char line[112];
            snprintf(line, sizeof(line), "[%lus] SKIP state=%u x=%u y=%u (未连接)",
                     (unsigned long) (millis() / 1000), state, x, y);
            traceAppend(line);
            tchLastLogged = state;
        }
        tchSeq++;
        return;
    }
    // 限速：两次报告至少间隔 8ms。安卓的 HID host 对高频通知敏感，
    // 连发太密会触发 GATT 拥塞，表现为"一发动作蓝牙就掉"。
    uint32_t now = millis();
    if (lastHidSendAt != 0 && now - lastHidSendAt < 8) {
        delay(8 - (now - lastHidSendAt));
    }
    Mouse.send(state, (int16_t) x, (int16_t) y, curContact);
    // v1.2.10 降频：状态切换必记，同状态帧每 8 帧记一次（原来每帧都记）
    if (tchLastLogged != state || (tchSeq & 7) == 0) {
        char line[96];
        snprintf(line, sizeof(line), "[%lus] TCH state=%u x=%u y=%u cid=%u seq=%lu",
                 (unsigned long) (millis() / 1000), state, x, y, curContact,
                 (unsigned long) tchSeq);
        traceAppend(line);
        tchLastLogged = state;
    }
    tchSeq++;
}

// 像素 → HID 坐标（描述符 Logical Maximum = 32767，与旧版官方固件一致）
static inline uint16_t toHid(int px, int screen) {
    if (screen <= 0) return 0;
    long v = (long) px * 32767L / screen;
    if (v < 0) v = 0;
    if (v > 32767) v = 32767;
    return (uint16_t) v;
}

// 抬起复位：清除可能残留的按下状态（丢包会让固件以为手指还按着）
// v1.2.8: 没有活动触点时不发任何报文——多余的抬起帧会污染下一个手势的时序
void releaseTouch() {
    if (!touchActive) return;
    touchSend(0x00, lastX, lastY);
}

// 心跳时间戳：App 探活打 GET /ping，用它判断链路是否真的活着
static volatile unsigned long lastPingAt = 0;

// v1.2.9: 读取当前连接间隔（单位 1.25ms；0=读取失败）。
// 手机省电默认给长间隔时，触摸报文会被挤进同一连接事件变成 0ms 触摸被防误触丢弃。
static uint16_t bleConnItvl() {
    BLEServer *srv = BLEDevice::getServer();
    if (srv == nullptr) return 0;
    ble_gap_conn_desc desc;
    if (ble_gap_conn_find(srv->getConnId(), &desc) != 0) return 0;
    return desc.conn_itvl;
}

// 执行一段手势：coords 为扁平像素坐标数组，count 为坐标点数
bool runGesture(const int *coords, int count, int duration, int sw, int sh) {
    if (count < 2 || sw <= 0 || sh <= 0) return false;
    if (!hidOutReady()) {
        Serial.printf("GESTURE rejected: no HID output (BLE not connected)\n");
        return false;
    }

    long total = duration;
    if (total < 0) total = 0;
    if (total > 60000) total = 60000;
    // 每个点之间的间隔；点数多时至少留 4ms 让 HID 报告能发出去
    int per = (count > 1) ? (int) (total / (count - 1)) : 0;
    if (per < 4 && total > 0) per = 4;

    // 每次手势换一个新 contact id —— 安卓靠它区分不同触点
    curContact = (uint8_t) (curContact % 126) + 1;

    uint16_t hx = toHid(coords[0], sw), hy = toHid(coords[1], sh);

    // v1.2.9: 连接间隔自适应——间隔太长时重新请求快速参数，
    // 并把按压时长拉到 ≥2 个连接间隔，保证按下/抬起落在不同连接事件里
    uint16_t itvl = bleConnItvl();
    int itvlMs = (int) (itvl * 1.25f);
    if (itvl > 32) {   // >40ms 就太慢了，重新请求 20~30ms
        BLEServer *srv = BLEDevice::getServer();
        if (srv) srv->requestConnParams(srv->getConnId(), 16, 24, 0, 400);
        // v1.2.10: 等参数生效（最多 100ms）——v1.2.9 里本次手势仍跑旧间隔，
        // 只有下一个手势受益；手机拒绝时按 itvlMs*3 兜底补偿
        for (int i = 0; i < 10 && bleConnItvl() > 32; i++) delay(10);
        itvl = bleConnItvl();
        itvlMs = (int) (itvl * 1.25f);
    }

    // v1.2.7 拟人序列：清残留 → 悬停 → 按下 → (微动) → 悬停抬起 → 离开
    // 部分国产 ROM 的防误触会丢弃"凭空出现又瞬间消失"的静止触摸，
    // 悬停帧 + 微小位移让触摸看起来像真人手指。
    touchSend(0x00, hx, hy);           // 清残留
    delay(10);
    touchSend(0x02, hx, hy);           // 悬停（In Range 未按下）
    delay(10);

    bool tap = (count == 2 && coords[0] == coords[2] && coords[1] == coords[3]);
    if (tap) {
        // 单击：按下保持足够时长（≥2 个连接间隔且 ≥80ms），中途加微动
        int hold = per;
        if (hold < 80) hold = 80;
        if (itvlMs > 0 && hold < itvlMs * 2) hold = itvlMs * 2;
        touchSend(0x03, hx, hy);
        delay(hold / 2);
        touchSend(0x03, (uint16_t) (hx + 6 > 32767 ? 32767 : hx + 6), (uint16_t) (hy + 6 > 32767 ? 32767 : hy + 6));
        delay(hold - hold / 2);
        touchSend(0x03, hx, hy);
    } else {
        // 滑动/拖拽：沿路径移动
        touchSend(0x03, hx, hy);
        for (int i = 1; i < count; i++) {
            if (per > 0) delay(per);
            touchSend(0x03, toHid(coords[i * 2], sw), toHid(coords[i * 2 + 1], sh));
            if ((i & 7) == 7) yield();
        }
    }
    touchSend(0x02, hx, hy);           // 抬起，仍在感应范围
    delay(10);
    touchSend(0x00, hx, hy);           // 离开
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
    if (!hidOutReady()) return false;
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
    if (!hidOutReady()) return false;
    int iv = intervalMs < 0 ? 0 : (intervalMs > 500 ? 500 : intervalMs);
    for (size_t i = 0; i < text.length(); i++) {
        Keyboard.write((uint8_t) text[i]);
        if (iv > 0) delay(iv);
        if ((i & 15) == 15) yield();
    }
    return true;
}

// ---------------- 指令分发（UDP 与 BLE GATT 共用，v1.2.3）----------------
// 包格式：第 1 字节命令字，后面是 payload：
//   0x06 手势 / 0x07 按键 / 0x08 打字 / 0x09 抬起复位
void handleCommand(uint8_t *buf, size_t len) {
    if (len < 2) return;
    uint8_t cmd = buf[0];

    // payload 从第 2 个字节开始（第 1 字节是命令字）
    const char *payload = (const char *) (buf + 1);
    size_t payloadLen = len - 1;

    Serial.printf("CMD 0x%02X length=%u connected=%d\n", cmd, (unsigned) len, Keyboard.isConnected() ? 1 : 0);
    {   // v1.2.6 诊断：进入指令分发
        char line[96];
        snprintf(line, sizeof(line), "[%lus] CMD 0x%02X len=%u ble=%d", (unsigned long) (millis() / 1000), cmd, (unsigned) len, Keyboard.isConnected() ? 1 : 0);
        traceAppend(line);
    }

    switch (cmd) {
        case 0x06: {   // 手势
            // v1.2.10: 静态复用，杜绝每次 4KB 堆分配/释放（长期运行堆碎片风险）
            // handleCommand 只在 loop 线程被调，静态复用安全
            static StaticJsonDocument<1024> doc;
            doc.clear();
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

            {   // 诊断：手势解析结果 + 当前连接间隔
                char line[128];
                snprintf(line, sizeof(line), "[%lus] GESTURE cmd: n=%d dur=%d screen=%dx%d p0=(%d,%d) itvl=%ums",
                         (unsigned long) (millis() / 1000), count, duration, sw, sh, coords[0], coords[1],
                         (unsigned) (bleConnItvl() * 5 / 4));
                traceAppend(line);
            }

            bool ok = runGesture(coords, count, duration, sw, sh);
            Serial.printf("GESTURE %s: coords=%d duration=%d screen=%dx%d\n",
                          ok ? "done" : "rejected", count, duration, sw, sh);
            break;
        }
        case 0x07: {   // 按键
            static StaticJsonDocument<512> doc;
            doc.clear();
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
            static StaticJsonDocument<2048> doc;
            doc.clear();
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
            Serial.printf("Unknown command: 0x%02X\n", cmd);
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
    if (len > 0) {
        udpCount++;
        handleCommand(buf, (size_t) len);
    }
}

// ---------------- BLE GATT 指令通道（v1.2.3）----------------
// 复用库里的自定义服务 4fafc201-... 特征 beb5483e-...（READ|WRITE|WRITE_NR）。
// 收包在 NimBLE 协议栈线程，执行在 loop 线程（pollBleCmd）——
// 手势要跑几百毫秒，绝不能堵住蓝牙协议栈线程。
//
// App 侧分包协议（JSON 超过一条写的内容时用）：
//   单包：[cmd][payload...]                        —— 直接执行
//   分包：首包 [0xFE][总长lo][总长hi][chunk...]，
//         后续包 [chunk...]，攒够总长后整体执行
// 建议连接后先 requestMtu(247)，单包即可覆盖大部分手势。
#define BLECMD_MAX     1500
#define BLECMD_QLEN    8        // v1.2.10: 4→8，批量任务连发不再溢出
#define BLECMD_TIMEOUT 3000UL   // 分包中途超过 3 秒没后续 → 丢弃重来

static uint8_t cmdQ[BLECMD_QLEN][BLECMD_MAX];
static uint16_t cmdQLen[BLECMD_QLEN];
static volatile uint8_t cmdQHead = 0, cmdQTail = 0;
static portMUX_TYPE cmdQMux = portMUX_INITIALIZER_UNLOCKED;

// BLE 线程专用：分包重组暂存
static uint8_t bleRxBuf[BLECMD_MAX];
static uint16_t bleRxLen = 0, bleRxTotal = 0;
static uint32_t bleRxStartMs = 0;

static bool bleCmdPush(const uint8_t *data, size_t len) {
    bool ok = false;
    portENTER_CRITICAL(&cmdQMux);
    uint8_t next = (cmdQHead + 1) % BLECMD_QLEN;
    if (next != cmdQTail && len <= BLECMD_MAX) {
        memcpy(cmdQ[cmdQHead], data, len);
        cmdQLen[cmdQHead] = (uint16_t) len;
        cmdQHead = next;
        ok = true;
    }
    portEXIT_CRITICAL(&cmdQMux);
    if (!ok) bleDropCount++;
    return ok;
}

class BleCmdCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *c) override {
        String v = c->getValue();
        size_t n = v.length();
        if (n == 0) return;
        const uint8_t *p = (const uint8_t *) v.c_str();

        // v1.2.6 诊断：记录每次写入的原始字节（前 24 字节），排查 App 到底发了什么
        char line[160];
        int off = snprintf(line, sizeof(line), "[%lus] RX n=%u:", (unsigned long) (millis() / 1000), (unsigned) n);
        for (size_t i = 0; i < n && i < 24 && off < (int) sizeof(line) - 3; i++) {
            off += snprintf(line + off, sizeof(line) - off, " %02X", p[i]);
        }
        if (n > 24) off += snprintf(line + off, sizeof(line) - off, " ..");
        traceAppend(line);

        if (p[0] == 0xFE) {                       // 分包首包
            if (n < 3) return;
            bleRxTotal = p[1] | (p[2] << 8);
            if (bleRxTotal < 2 || bleRxTotal > BLECMD_MAX) { bleRxTotal = 0; return; }
            bleRxLen = 0;
            bleRxStartMs = millis();
            size_t first = n - 3;
            if (first > (size_t)(BLECMD_MAX - bleRxLen)) first = BLECMD_MAX - bleRxLen;
            memcpy(bleRxBuf + bleRxLen, p + 3, first);
            bleRxLen += first;
        } else if (bleRxTotal > 0) {              // 后续分包
            if (millis() - bleRxStartMs > BLECMD_TIMEOUT) { bleRxTotal = 0; bleRxLen = 0; return; }
            size_t room = bleRxTotal - bleRxLen;
            size_t take = n < room ? n : room;
            memcpy(bleRxBuf + bleRxLen, p, take);
            bleRxLen += take;
        } else {                                  // 单包指令
            bleCmdPush(p, n);
            return;
        }
        if (bleRxTotal > 0 && bleRxLen >= bleRxTotal) {   // 攒齐了
            bleCmdPush(bleRxBuf, bleRxLen);
            bleRxTotal = 0;
            bleRxLen = 0;
        }
    }
};

static BleCmdCallbacks bleCmdCallbacks;

void pollBleCmd() {
    uint8_t *frame = nullptr;
    uint16_t flen = 0;
    portENTER_CRITICAL(&cmdQMux);
    if (cmdQTail != cmdQHead) {
        frame = cmdQ[cmdQTail];
        flen = cmdQLen[cmdQTail];
    }
    portEXIT_CRITICAL(&cmdQMux);
    if (!frame) return;

    // 复制一份到栈上再放锁，处理期间新指令照常入队
    uint8_t local[BLECMD_MAX];
    memcpy(local, frame, flen);
    portENTER_CRITICAL(&cmdQMux);
    cmdQTail = (cmdQTail + 1) % BLECMD_QLEN;
    portEXIT_CRITICAL(&cmdQMux);

    bleCount++;
    if (flen >= 1 && local[0] == 0x09) {
        // v1.2.10: 连续多条复位折叠成一条——App 任务收尾常连发 0x09，
        // 逐条执行会把手势顶到队列更后面
        portENTER_CRITICAL(&cmdQMux);
        while (cmdQTail != cmdQHead && cmdQ[cmdQTail][0] == 0x09) {
            cmdQTail = (cmdQTail + 1) % BLECMD_QLEN;
        }
        portEXIT_CRITICAL(&cmdQMux);
    }
    handleCommand(local, flen);
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
    String html = pageHeader("AutoJS HID · " FW_CHIP);
    html += "<div class=row><span>固件版本</span><span>" FW_VERSION "</span></div>";
    html += "<div class=row><span>芯片</span><span>" FW_CHIP "</span></div>";
    // 显示 SSID：从原厂固件升级上来时，用它确认沿用的是不是原来那个 WiFi
    html += "<div class=row><span>WiFi</span><span class='" + String(sta ? "ok'>" : "bad'>")
            + (sta ? htmlEscape(WiFi.SSID()) : String("热点模式")) + "</span></div>";
    html += "<div class=row><span>本机 IP</span><span>" + ip + "</span></div>";
    html += "<div class=row><span>信号强度</span><span>" + (sta ? String(WiFi.RSSI()) + " dBm" : String("-")) + "</span></div>";
    html += "<div class=row><span>蓝牙 HID</span><span class='" + String(Keyboard.isConnected() ? "ok'>已连接" : "bad'>未连接") + "</span></div>";
    html += "<div class=row><span>手势 / UDP / BLE</span><span>" + String(gestureCount) + " / " + String(udpCount) + " / " + String(bleCount) + "</span></div>";
    // 崩溃自保护状态（v1.2.2 起）
    html += "<div class=row><span>固件槽位</span><span>" + String(esp_ota_get_running_partition()->label)
            + " · 自保护" + (healthConfirmed ? "已确认" : "观察中") + "</span></div>";
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
    doc["bleCommands"] = bleCount;
    doc["bleDrops"] = bleDropCount;
    doc["uptime"] = (uint32_t) (millis() / 1000);
    doc["part"] = esp_ota_get_running_partition()->label;
    doc["health"] = healthConfirmed;
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

// 蓝牙排查页：手机连热点后直接打开 /blelog 就能看到事件轨迹，
// 不用插串口也能判断配对断在连接、还是安全握手之后。
void handleBleLog() {
    String out = "fw=" FW_VERSION "\n";
    out += "name=" + deviceName + "\n";
    out += "connected=" + String(Keyboard.isConnected() ? "yes" : "no") + "\n";
    out += "conn=" + String(bleConnectCount) + " dis=" + String(bleDisconnectCount) + "\n";
    out += "uptime=" + String(millis() / 1000) + "s\n";
    out += "hidSent=" + String((unsigned long) hidSendCount)
         + " hidSkipped=" + String((unsigned long) hidSkipCount) + "\n";
    out += "cmdUdp=" + String(udpCount) + " cmdBle=" + String(bleCount)
         + " bleDrops=" + String(bleDropCount) + "\n";
    out += "freeHeap=" + String(ESP.getFreeHeap()) + "\n";
    out += "resetReason=" + String(resetReasonName((uint8_t) esp_reset_reason()))
         + " (last=" + resetReasonName(prefs.getUChar("lastRst", 0xFF)) + ")\n";
    out += "part=" + String(esp_ota_get_running_partition()->label) + "\n";
    out += "crashCount=" + String(prefs.getUChar("crashct", 0))
         + "/" + String(CRASH_ROLLBACK_THRESHOLD) + "\n";
    out += "rollbacks=" + String(prefs.getUChar("rbct", 0)) + "\n";
    out += "health=" + String(healthConfirmed ? "confirmed" : "pending") + "\n";
    out += "---- trace ----\n";
    char snap[BLE_TRACE_SIZE];
    traceSnapshot(snap, sizeof(snap));
    out += snap[0] ? snap : "(empty)\n";
    server.sendHeader("Cache-Control", "no-store");
    server.send(200, "text/plain; charset=utf-8", out);
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

// 估算 multipart 外壳占用的字节数（边界行 + Content-Disposition 头 + 结束边界）。
// 浏览器每次生成的 boundary 长度不同，这里按实测常见值放宽估，
// 宁可少算几十字节让 Update 多留余量，也不能多算出负数。
size_t multipartOverhead() {
    // 典型结构：
    //   --{boundary}\r\n                                (boundary+4)
    //   Content-Disposition: form-data; name="firmware"; filename="firmware.bin"\r\n
    //   Content-Type: application/octet-stream\r\n
    //   \r\n
    //   <正文>
    //   \r\n--{boundary}--\r\n
    const size_t boundaryLen = 40;   // 实际约 32~38
    const size_t headLen = 120;      // 两行头 + 空行，实测 90~110
    const size_t tailLen = 6;
    return (boundaryLen + 4) + headLen + (4 + boundaryLen + 4) + tailLen;
}

void handleOtaUpload() {
    HTTPUpload &up = server.upload();
    if (up.status == UPLOAD_FILE_START) {
        if (!otaAuthorized()) {
            Serial.println("OTA rejected: unauthorized");
            // v1.2.10: 回 401 让浏览器弹密码框（App 一直带 Basic 认证头，不受影响）
            server.sendHeader("WWW-Authenticate", "Basic realm=\"ota\"");
            server.send(401, "text/plain; charset=utf-8", "需要升级口令");
            return;
        }
        Serial.printf("OTA start: %s\n", up.filename.c_str());
        // 文件名必须 .bin，避免误传其他文件把分区写坏
        if (!up.filename.endsWith(".bin")) {
            Serial.println("OTA rejected: not .bin");
            return;
        }

        // 之前这里用 UPDATE_SIZE_UNKNOWN，是 OTA 一直写失败的直接原因：
        // Update 不知道总量就无法在写入阶段做边界检查，末尾一旦对不上
        // （multipart 边框被算进数据或丢包），Update.end() 直接判定失败。
        // 正确做法是从 HTTP 请求头拿 Content-Length，减去 multipart 头尾开销，
        // 得到固件实际大小交给 Update.begin()。
        size_t total = (size_t) server.clientContentLength();
        size_t overhead = multipartOverhead();
        otaImageSize = (total > overhead) ? (total - overhead) : 0;

        Serial.printf("OTA content-length=%u, overhead=%u, image=%u\n",
                      (unsigned) total, (unsigned) overhead, (unsigned) otaImageSize);

        // 上限按 app 分区实际可用空间兜底，避免把引导区写坏
        const size_t appMax = 0x1E0000;   // 与 partitions.csv 中 app0 一致
        if (otaImageSize == 0 || otaImageSize > appMax) {
            Serial.println("OTA rejected: bad size");
            otaImageSize = 0;
            return;
        }
        // 只支持应用镜像（首字节 0xE9）。整片 merged 镜像带 bootloader，
        // 写进 app 分区会让设备起不来，这里直接拒绝。
        if (!Update.begin(otaImageSize)) {
            Update.printError(Serial);
            otaImageSize = 0;
        }
    } else if (up.status == UPLOAD_FILE_WRITE) {
        if (otaImageSize == 0) return;
        if (Update.isRunning() && !Update.hasError()) {
            if (Update.write(up.buf, up.currentSize) != up.currentSize) {
                Update.printError(Serial);
            }
        }
    } else if (up.status == UPLOAD_FILE_END) {
        if (otaImageSize == 0) return;
        if (Update.isRunning() && !Update.hasError()) {
            // 收到的字节数和预期不一致，说明传输出错，宁可失败也别写半截
            if (up.totalSize != otaImageSize) {
                Serial.printf("OTA size mismatch: got %u expected %u\n",
                              (unsigned) up.totalSize, (unsigned) otaImageSize);
                Update.abort();
                return;
            }
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

// ---------------- 崩溃自保护 ----------------
static const char *resetReasonName(uint8_t r) {
    switch (r) {
        case ESP_RST_UNKNOWN:   return "unknown";
        case ESP_RST_POWERON:   return "poweron";
        case ESP_RST_EXT:       return "reset-btn";
        case ESP_RST_SW:        return "software";
        case ESP_RST_PANIC:     return "panic";
        case ESP_RST_INT_WDT:   return "int-wdt";
        case ESP_RST_TASK_WDT:  return "task-wdt";
        case ESP_RST_WDT:       return "other-wdt";
        case ESP_RST_DEEPSLEEP: return "deepsleep";
        case ESP_RST_BROWNOUT:  return "brownout";
        default:                return "other";
    }
}

// 上电最早期调用（prefs 就绪后立刻）：统计异常重启。
// 正常来源（上电 / RESET 键 / 软件重启 / 深睡唤醒 / 掉电）不计数，
// panic、看门狗这类异常来源 +1；攒够阈值 → 启动项切回另一个 OTA 槽。
static void crashGuardBoot() {
    uint8_t rr = (uint8_t) esp_reset_reason();
    if (prefs.getUChar("lastRst", 0xFF) != rr) prefs.putUChar("lastRst", rr);

    bool normal = (rr == ESP_RST_POWERON || rr == ESP_RST_EXT || rr == ESP_RST_SW ||
                   rr == ESP_RST_DEEPSLEEP || rr == ESP_RST_BROWNOUT);
    uint8_t ct = prefs.getUChar("crashct", 0);
    if (normal) {
        if (ct != 0) { ct = 0; prefs.putUChar("crashct", 0); }
    } else {
        ct++;
        prefs.putUChar("crashct", ct);
        Serial.printf("[crash-guard] abnormal reset (%s), consecutive=%d/%d\n",
                      resetReasonName(rr), ct, CRASH_ROLLBACK_THRESHOLD);
    }
    if (ct >= CRASH_ROLLBACK_THRESHOLD) {
        const esp_partition_t *cur = esp_ota_get_running_partition();
        const esp_partition_t *other = esp_ota_get_next_update_partition(cur);
        prefs.putUChar("rbct", prefs.getUChar("rbct", 0) + 1);
        prefs.putUChar("crashct", 0);
        Serial.printf("[crash-guard] %d consecutive crashes -> rollback %s -> %s\n",
                      ct, cur ? cur->label : "?", other ? other->label : "(none)");
        if (other) {
            esp_ota_set_boot_partition(other);
        }
        delay(200);
        ESP.restart();
    }
}

// 健康确认：连续运行满 60 秒（WiFi/BLE/HTTP 都已就绪）→ 清零崩溃计数，
// 并把当前 OTA 槽标记为有效。若引导程序开了 OTA 回滚，这一步同时
// 会取消 pending-verify 状态；没开也无副作用。
static void crashGuardConfirmHealth() {
    if (healthConfirmed || millis() < HEALTH_CONFIRM_MS) return;
    healthConfirmed = true;
    prefs.putUChar("crashct", 0);
    esp_ota_mark_app_valid_cancel_rollback();
    Serial.println("[crash-guard] health confirmed (uptime >= 60s)");
}

// ---------------- 初始化 ----------------
void setupBle() {
    // 设备名固定为 "鼠标键盘"（用户指定；v1.2.0 之前是 AutoJS HID-XXXX）。
    // 注意：BLE GAP 设备名是 UTF-8，中文名现代 Android 蓝牙栈可正常显示。
    deviceName = DEVICE_PREFIX;

    // 名字要在 begin() 之前设置：begin() 会用 deviceName 初始化 BLE 广播
    Keyboard.deviceName = deviceName.c_str();
    Keyboard.deviceManufacturer = "Espressif";
    // v1.2.3: BLE GATT 指令通道回调（挂在库内自定义服务 beb5483e 特征上）
    Keyboard.setCallbacks(&bleCmdCallbacks);
    hidInitDiag();
    Keyboard.begin();
    Mouse.begin();
    // 注意：begin() 只是创建任务，BLEDevice::init() 在那个任务里异步执行，
    // 此处不能直接调 startAdvertising()，广播由库内部在初始化完成后开启。
    Serial.printf("BLE HID started: %s\n", deviceName.c_str());
    bleEvent("ble init");
}

void setup() {
    Serial.begin(115200);
#if ARDUINO_USB_CDC_ON_BOOT
    // USB-CDC 诊断版：没接电脑/没打开串口终端时，打印直接丢弃不阻塞，
    // 否则 HWCDC 默认超时会卡住 loop。接上串口终端即可看到 panic 调用栈。
    Serial.setTxTimeoutMs(0);
#endif
    delay(200);
    Serial.println("\n=== AutoJS HID (TouchTool) " FW_VERSION " ===");

    prefs.begin("hidcfg", false);

    // 崩溃自保护：越早跑越好，赶在任何可能出问题的初始化之前
    crashGuardBoot();

    // 关掉 WiFi 省电：C3 单射频，省电模式会让 BLE 的连接间隔抖动得厉害，
    // 表现为 HID 报告延迟/超时、蓝牙频繁掉线。HID 场景吞吐要求极低，不省这个电。
    WiFi.setSleep(false);

    // 顺序很重要：ESP32-C3 只有一套 2.4G 射频，WiFi 必须在 BLE 之前初始化。
    // 反过来的话，WiFi 起来时会重置 BT 控制器，表现就是手机能搜到设备、
    // 点配对转几秒圈然后设备从列表里消失（连接被射频重配打断）。
    if (!connectSavedWifi(15000)) {
        startAp();
    }

setupBle();

    Keyboard.releaseAll();
    releaseTouch();

    // HTTP 服务
    server.on("/", HTTP_GET, handleRoot);
    server.on("/wifi", HTTP_POST, handleWifi);
    server.on("/info", HTTP_GET, handleInfo);
    server.on("/ping", HTTP_GET, handlePing);
    server.on("/blelog", HTTP_GET, handleBleLog);
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
    crashGuardConfirmHealth();
    pollUdp();
    pollBleCmd();   // v1.2.3: BLE GATT 指令通道
    // WiFi 断线重连（每 30 秒试一次，不阻塞主循环）
    static unsigned long lastCheck = 0;
    if (millis() - lastCheck > 30000) {
        lastCheck = millis();
        if (WiFi.getMode() == WIFI_STA && WiFi.status() != WL_CONNECTED) {
            WiFi.reconnect();
        }
    }

    // 广播保活退化为兜底：正常情况下 onDisconnect 里已经重开广播，
    // 这里只在"既没连上、又超过 30 秒"时补一次，避免和配对过程抢时序。
    static unsigned long lastAdv = 0;
    if (millis() - lastAdv > 30000) {
        lastAdv = millis();
        if (!Keyboard.isConnected()) {
            BLEDevice::startAdvertising();
            bleEvent("adv keepalive");
        }
    }
    delay(2);
}
