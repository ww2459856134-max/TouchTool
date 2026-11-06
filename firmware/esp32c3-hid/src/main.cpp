/*
 * TouchTool HID 固件 (ESP32-C3/S3)
 * ------------------------------------------------------------------
 * 兼容原厂 AutoJS HID 的 UDP 协议，并新增：
 *   0x06 手势      {"a":[x1,y1,x2,y2...],"b":时长ms,"c":屏宽,"d":屏高}
 *   0x07 按键      {"k":"BACK"}                       ← 新增
 *   0x08 键盘打字  {"t":"abc","i":按键间隔ms}          ← 新增
 *   0x09 抬起复位  {}                                  ← 新增
 *   0x0A 鼠标滚轮  {"d":±格数,"h":±横滚,"n":次数,"i":间隔ms,"m":0|1,"x":px,"y":px} ← v1.5.0 新增；
 *                  v1.5.1 加 m 模式覆盖与 drag 兜底；v1.5.2 加 x/y 指定 drag 滑动区域中心
 *
 * HTTP（端口 80）：
 *   GET  /          状态页（WiFi / BLE / 版本）+ 配网表单 + 固件升级表单
 *   POST /wifi      保存 WiFi 并重启
 *   GET  /info      返回 JSON 版本信息（App 用来判断是否可升级）
 *   GET  /ping      轻量探活，返回 "pong <版本>"
 *   GET  /blelog    蓝牙诊断页（?tch=1 打开逐帧触摸轨迹）
 *   POST /update    OTA 固件上传（multipart，字段名 firmware）
 *
 * 关键设计：
 *   - 触摸用 Digitizer HID 绝对坐标 0~32767（复刻旧版官方固件），按包里的屏幕尺寸换算
 *   - 分区表为双 app（ota_0 / ota_1），升级写另一块，失败自动回滚
 *
 * ==================== v1.2.9-fix1 修复清单 ====================
 * P0-1  hidSendCount 从未自增        → touchSend 成功路径补 ++，/blelog 数据恢复可信
 * P0-2  bleEvent("onConnect") 无调用 → 新增 pollBleState()，连接/断开计数与轨迹真实有效
 * P0-3  FW_CHIP 硬编码 ESP32-C3      → 运行时 ESP.getChipModel()，S3 版本不再谎报
 * P0-4  sendText 非 ASCII 误发修饰键 → 显式拒绝 >0x7E，并回错误信息
 * P1-5  手势同步阻塞整个 loop        → waitYield()/pumpIo()，长手势期间 HTTP、UDP 不再饿死
 * P1-6  duration 被 8ms 限速覆盖     → 绝对时间轴驱动，限速只作物理下限
 * P1-7  临界区内 memcpy 1.5KB        → 队列改互斥量保护（不关中断）
 * P1-8  每帧都写 trace 并 memmove 2.4KB → 环形行缓冲 + 分级开关（TCH 默认关）
 * P1-9  bleConnItvl 每次手势查两次   → 每次指令只查一次并下传，改参请求加 5s 节流
 * P1-10 单击微动 hx+6 越过 32767     → clampHid()，改往回偏
 * P1-11 a 数组奇数长度未校验         → 直接拒绝
 * P1-12 上电等 WiFi 15s 才起 BLE     → WiFi.begin() 异步化，BLE 立即可用
 * P1-13 /blelog 2400 字节栈缓冲      → 改静态缓冲
 * P2-14 multipart 开销靠猜           → 从 Content-Type 解析 boundary 精确计算
 * P2-15 .bin 后缀大小写敏感          → 转小写比较
 * 其它  setupBle() 缩进、esp_mac.h 重复、AP 模式重置 setSleep 等
 * ==============================================================
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>
#include <Update.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <BleCombo.h>
#include <BLEDevice.h>
#include <host/ble_gap.h>
#include <host/ble_store.h>
#include <vector>
#include <SPIFFS.h>
#include <mbedtls/base64.h>
#include <esp_mac.h>
#include <nvs.h>
#include <esp_ota_ops.h>
#include <esp_system.h>
#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>

// ---------------- 配置 ----------------
#define FW_VERSION   "1.5.13"
#define UDP_PORT     21115
#define HTTP_PORT    80
#define AP_PASSWORD  "autojs1234"      // 与原厂固件一致，便于老用户沿用
#define DEVICE_PREFIX "鼠标键盘"

// 串口详细日志。追求极限点击精度（115200 下一条 60 字节约 5ms）可设 0。
#define FW_VERBOSE   1

// 升级接口的口令：防止同一 WiFi 下任何人随手把固件换掉。
// App 端固定带 Authorization: Basic base64("admin:autojs1234")
#define OTA_USER     "admin"
#define OTA_PASS     "autojs1234"
// 允许不带口令就升级吗？保持 1 是为了兼容 v90 之前、不带认证头的老 App。
// 想要更安全就设 0（浏览器手刷会弹一次认证框）。
#define OTA_ALLOW_NO_AUTH 1

// ---------------- 崩溃自保护 ----------------
// 目标：杜绝「刷了坏固件 → 无限重启循环」变砖。
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
String chipName = "unknown";     // v1.2.9-fix1: 运行时读取，不再硬编码 C3
uint32_t gestureCount = 0;
uint32_t udpCount = 0;
uint32_t bleCount = 0;
uint32_t bleDropCount = 0;
uint32_t udpDropCount = 0;       // v1.2.9-fix1: UDP 入队失败的计数（原来看不到）
String lastError = "";

// ---------------- UDP 来源白名单（网页可开关）----------------
// 开启后仅接受指定 IP 的手势指令，防同 WiFi 下他人遥控点击。
// 不默认开启：手机 DHCP 换 IP 后忘记更新会把 App 锁在外面，只能网页关掉。
bool udpWlOn = false;
String udpWlIp = "";
uint32_t udpWlDropCount = 0;

// ---------------- v1.5.4 连接优化 ----------------
// 热点常开（AP+STA 共存）：连上路由器后热点仍保持，随时可连 192.168.4.1 救急改配置。
// 默认关闭——多一路射频略增功耗，C3 堆紧张时可选。NVS "apsta"。
bool apStaOn = false;
static bool apStaApplied = false;
// WiFi 重连指数退避：断线 2s 首试，之后 5s/15s/30s/60s 封顶，连上复位
unsigned long wifiBackoffMs = 2000;
unsigned long lastWifiTry = 0;
// BLE 快广播窗口：断开后 30 秒内用 20-30ms 间隔广播（手机扫到即回连，1 秒级），
// 之后自动降回慢速省电
unsigned long fastAdvUntil = 0;
bool advSlowed = false;

// ---------------- v1.5.1 滚轮诊断与兜底 ----------------
// 1.5.0 实测：0x0A 指令固件收得到、也发了 relMouse 通知，但部分安卓主机上
// 滚轮无效果。原因只能在主机侧（CCCD 订阅 / 事件分发），固件能做的是：
//   a) 把 relMouse 通道的通知成败、订阅数、报告句柄暴露到 /blelog，先确诊；
//   b) 提供 drag 兜底模式——用已被验证有效的 digitizer 触摸通道模拟滑动滚动，
//      在任何安卓上都生效（代价是屏幕中心会有一次真实滑动）。
uint32_t relNtfyOk = 0;        // relMouse 通知被协议栈接受次数（库内统计）
uint32_t relNtfyFail = 0;      // relMouse 通知失败次数
uint8_t  wheelMode = 0;        // 0=hid(HID 原生滚轮) 1=drag(触摸滑动兜底)
uint16_t wheelStep = 120;      // drag 模式每格像素
uint16_t gScrW = 0, gScrH = 0; // 最近一次手势的屏幕尺寸（drag 模式定位用）
uint16_t scrWFix = 0, scrHFix = 0; // 网页手动指定的屏幕尺寸（0=用手势缓存）

// 崩溃自保护：60 秒健康确认是否已通过
static bool healthConfirmed = false;

// 所有 IO（HTTP + UDP 收包）是否已初始化完毕。
// setup() 里 server.begin()/udp.begin() 之后才置 true，
// 防止 waitYield 在 setup 早期调用到未初始化的 server 而崩。
static bool ioReady = false;

// 前向声明：/blelog 页面要用（实现在文件后段「崩溃自保护」）
static const char *resetReasonName(uint8_t r);

// ---------------- BLE 事件轨迹 ----------------
// v1.2.9-fix1: 改成固定行数的环形缓冲，不再每次 memmove 2.4KB。
// 写入方（BLE 协议栈线程）与读取方（loop 线程）用临界区保护；
// 单次只 memcpy 一行（≤95 字节），关中断时间从 ~15us 降到 <1us。
#define TRACE_LINES     32
#define TRACE_LINE_LEN  96
static char traceRing[TRACE_LINES][TRACE_LINE_LEN];
static volatile uint8_t traceHead = 0;
static volatile uint8_t traceCount = 0;
static portMUX_TYPE traceMux = portMUX_INITIALIZER_UNLOCKED;

// 分级开关：常态只记指令级事件（每条指令 1 行），
// 逐帧触摸轨迹（一次手势几十行）默认关闭，需要时访问 /blelog?tch=1 打开。
static volatile bool traceOn = true;
static volatile bool traceTch = false;

// 非静态：库（BleComboKeyboard.cpp）也引用，链接需跨翻译单元可见
void traceAppend(const char *line);  // 前置声明，供库侧 extern 使用
void traceAppend(const char *line) {
    if (!traceOn) return;
    size_t n = strlen(line);
    if (n >= TRACE_LINE_LEN) n = TRACE_LINE_LEN - 1;
    portENTER_CRITICAL(&traceMux);
    uint8_t h = traceHead;
    memcpy(traceRing[h], line, n);
    traceRing[h][n] = 0;
    traceHead = (uint8_t) ((h + 1) % TRACE_LINES);
    if (traceCount < TRACE_LINES) traceCount++;
    portEXIT_CRITICAL(&traceMux);
}

// 按时间顺序导出最近 N 行，返回写入 out 的字节数
static size_t traceDump(char *out, size_t outSize) {
    if (outSize == 0) return 0;
    portENTER_CRITICAL(&traceMux);
    uint8_t cnt = traceCount;
    uint8_t h = traceHead;
    size_t off = 0;
    uint8_t start = (uint8_t) ((h + TRACE_LINES - cnt) % TRACE_LINES);
    for (uint8_t i = 0; i < cnt; i++) {
        const char *l = traceRing[(start + i) % TRACE_LINES];
        size_t n = strlen(l);
        if (off + n >= outSize - 1) break;
        memcpy(out + off, l, n);
        off += n;
    }
    out[off] = 0;
    portEXIT_CRITICAL(&traceMux);
    return off;
}

uint16_t bleConnectCount = 0;
uint16_t bleDisconnectCount = 0;
static bool lastBleConnState = false;

void bleEvent(const char *msg) {
    if (strcmp(msg, "onConnect") == 0) bleConnectCount++;
    if (strcmp(msg, "onDisconnect") == 0) bleDisconnectCount++;
    char line[TRACE_LINE_LEN];
    snprintf(line, sizeof(line), "[%lus] %s conn=%d dis=%d\n",
             (unsigned long) (millis() / 1000), msg,
             (int) bleConnectCount, (int) bleDisconnectCount);
    traceAppend(line);
#if FW_VERBOSE
    Serial.print("[BLE-TRACE] ");
    Serial.print(line);
#endif
}

// v1.2.9-fix1: 原代码没有任何地方调用 bleEvent("onConnect")，
// 导致 /blelog 的 conn=/dis= 恒为 0，排查时被误读成「从没连过」。
// 这里在 loop 里检测连接状态跳变来补上（不依赖库内部回调，零侵入）。
// 断开时顺手重开广播，比原来 30 秒兜底更跟手。

// ==================== v1.5.8: 配对密钥（bond）诊断 ====================
// 背景：nvs_entry_find 枚举在 IDF5 上不可靠（v1.5.6 nvsAll=-1 的根源），
// 改用 NimBLE 官方存储接口 ble_store_util_count —— 它和协议栈读写的是
// 同一份数据（NVS_PERSIST 已确认打开），数字不再受枚举 API 影响。
// own  = 本机密钥（LTK/IRK，重启后回连加密靠它）
// peer = 对端（手机）密钥
static int bondCount(int objType) {
    int n = 0;
    return ble_store_util_count(objType, &n) == 0 ? n : -1;
}
static void bondTrace(const char *tag) {
    char line[TRACE_LINE_LEN];
    snprintf(line, sizeof(line), "[%lus] %s own=%d peer=%d\n",
             (unsigned long) (millis() / 1000), tag,
             bondCount(BLE_STORE_OBJ_TYPE_OUR_SEC),
             bondCount(BLE_STORE_OBJ_TYPE_PEER_SEC));
    traceAppend(line);
}

// GAP 事件监听：直接从协议栈底层记录配对/加密/断开事件（带 reason 码）。
// 之前只能看到 onConnect/onDisconnect，断开原因（比如加密失败 0x06/0x0f）
// 完全不可见 —— "重启后要重新配对"的关键证据一直缺失。
static struct ble_gap_event_listener s_gapListener;
static void bondBackupSaveNow(const char *why);   // 见下方 v1.5.9 备份实现
// v1.5.10: 备份写入不再在协议栈任务里做 —— NimBLE host 任务栈只有几 KB，
// NVS 写入（Preferences/commit）深调用曾直接把任务栈打爆（resetReason=panic）。
// 回调里只置标志，由 loop 主线程执行。
static volatile bool bkpReq = false;
static volatile bool bkpWrittenOnce = false;   // v1.5.11: 巡检开关
static int gapEventCb(struct ble_gap_event *event, void *arg) {
    (void)arg;
    char line[TRACE_LINE_LEN];
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT: {
        // 对端 OTA 地址：Android 常用 RPA（每 15 分钟轮换），
        // 若这里每次地址都不同而 bond 又按地址存，回连就会找不到密钥
        struct ble_gap_conn_desc desc;
        if (ble_gap_conn_find(event->connect.conn_handle, &desc) == 0) {
            const ble_addr_t *a = &desc.peer_ota_addr;
            snprintf(line, sizeof(line), "[%lus] gap conn t=%d %02x:%02x:%02x:%02x:%02x:%02x\n",
                     (unsigned long) (millis() / 1000), (int) a->type,
                     a->val[5], a->val[4], a->val[3], a->val[2], a->val[1], a->val[0]);
            traceAppend(line);
        }
        break;
    }
    case BLE_GAP_EVENT_DISCONNECT:
        snprintf(line, sizeof(line), "[%lus] gap disc rsn=0x%02x\n",
                 (unsigned long) (millis() / 1000), event->disconnect.reason);
        traceAppend(line);
        break;
    case BLE_GAP_EVENT_ENC_CHANGE:
        // 加密结果：st=0 成功；随后立刻统计 bond 数，
        // 若配对成功后 own/peer>0 而重启后归零 → 密钥没存住；若一直为 0 → 配对根本没发生绑定
        snprintf(line, sizeof(line), "[%lus] gap enc st=%d own=%d peer=%d\n",
                 (unsigned long) (millis() / 1000), event->enc_change.status,
                 bondCount(BLE_STORE_OBJ_TYPE_OUR_SEC),
                 bondCount(BLE_STORE_OBJ_TYPE_PEER_SEC));
        traceAppend(line);
        if (event->enc_change.status == 0) {
            // v1.5.10: 只置标志，备份由 loop 主线程执行（防协议栈任务栈溢出）
            bkpReq = true;
        }
        break;
    case BLE_GAP_EVENT_PASSKEY_ACTION:
        snprintf(line, sizeof(line), "[%lus] gap pkact=%d\n",
                 (unsigned long) (millis() / 1000), event->passkey.params.action);
        traceAppend(line);
        break;
    case BLE_GAP_EVENT_REPEAT_PAIRING: {
        // 对端带着旧密钥来配对（手机换机/密钥不同步时出现）。
        // 默认行为是 IGNORE（新配对被拒），这里删掉旧 bond 再允许重配。
        struct ble_gap_conn_desc desc;
        if (ble_gap_conn_find(event->repeat_pairing.conn_handle, &desc) == 0) {
            ble_store_util_delete_peer(&desc.peer_id_addr);
        }
        snprintf(line, sizeof(line), "[%lus] gap repeat-pair -> retry\n",
                 (unsigned long) (millis() / 1000));
        traceAppend(line);
        return BLE_GAP_REPEAT_PAIRING_RETRY;
    }
    case BLE_GAP_EVENT_IDENTITY_RESOLVED:
        traceAppend("[gap] identity-resolved\n");
        break;
    default:
        break;
    }
    return 0;
}

// ==================== v1.5.9: bond 自备份（釜底抽薪） ====================
// 实测证据（v1.5.8）：配对后 own=1 peer=1（协议栈 RAM 层写入成功），
// 重启后 bonds boot own=0 peer=0 而 NVS used 条目数不变 ——
// 内置的 NVS 持久化"写不回读不出"，具体断点在预编译库里无法修复。
// 对策：不再依赖它。每次绑定成功后把密钥结构体原样备份到自己的
// NVS 命名空间（ttbond）；开机时若协议栈 store 为空且备份存在，
// 用官方 API ble_store_write_our_sec/peer_sec/cccd 重新注入。
// 结构体是纯 POD（ble_store_value_sec），同版本编译无布局差异，可整体 memcpy。
struct BondBackup {
    std::vector<ble_store_value_sec> own;
    std::vector<ble_store_value_sec> peer;
    std::vector<ble_store_value_cccd> cccd;
};

static int bbCollectCb(int objType, union ble_store_value *val, void *arg) {
    BondBackup *b = (BondBackup *)arg;
    if (objType == BLE_STORE_OBJ_TYPE_OUR_SEC)      b->own.push_back(val->sec);
    else if (objType == BLE_STORE_OBJ_TYPE_PEER_SEC) b->peer.push_back(val->sec);
    else if (objType == BLE_STORE_OBJ_TYPE_CCCD)     b->cccd.push_back(val->cccd);
    return 0;
}

static bool bondBackupCollect(BondBackup &b) {
    ble_store_iterate(BLE_STORE_OBJ_TYPE_OUR_SEC,  bbCollectCb, &b);
    ble_store_iterate(BLE_STORE_OBJ_TYPE_PEER_SEC, bbCollectCb, &b);
    ble_store_iterate(BLE_STORE_OBJ_TYPE_CCCD,     bbCollectCb, &b);
    return b.own.size() + b.peer.size() > 0;
}

#define BB_MAGIC 0x54544231  // "TTB1"
#define BB_VER    1

static bool bondBackupWrite(BondBackup &b) {
    // 头: magic(4) ver(2) nOwn(2) nPeer(2) nCccd(2) = 12 字节
    std::vector<uint8_t> blob(12
        + b.own.size()  * sizeof(ble_store_value_sec)
        + b.peer.size() * sizeof(ble_store_value_sec)
        + b.cccd.size() * sizeof(ble_store_value_cccd));
    uint8_t *q = blob.data();
    const uint32_t magicV = BB_MAGIC;
    memcpy(q, &magicV, 4); q += 4;
    uint16_t v = BB_VER, n;
    v = BB_VER; memcpy(q, &v, 2); q += 2;
    n = (uint16_t) b.own.size();  memcpy(q, &n, 2); q += 2;
    n = (uint16_t) b.peer.size(); memcpy(q, &n, 2); q += 2;
    n = (uint16_t) b.cccd.size(); memcpy(q, &n, 2); q += 2;
    if (!b.own.empty())
        memcpy(q, b.own.data(), b.own.size() * sizeof(ble_store_value_sec)), q += b.own.size() * sizeof(ble_store_value_sec);
    if (!b.peer.empty())
        memcpy(q, b.peer.data(), b.peer.size() * sizeof(ble_store_value_sec)), q += b.peer.size() * sizeof(ble_store_value_sec);
    if (!b.cccd.empty())
        memcpy(q, b.cccd.data(), b.cccd.size() * sizeof(ble_store_value_cccd));
    size_t len = (size_t) (q - blob.data());
    bool ok = false;
    unsigned char b64[400];
    size_t olen = 0;
    if (mbedtls_base64_encode(b64, sizeof(b64), &olen, blob.data(), len) != 0) {
        traceAppend("[bkp] b64 encode fail\n");
        return false;
    }
    String s((char *) b64, olen);

    // v1.5.13: 关键发现 —— 这台设备上凡是跨多条目的 NVS 变长写入（blob>12B、
    // 字符串>12B）都会"写入返回成功但立即读回 NOT_FOUND"（v1.5.11 实测 188B blob、
    // v1.5.12 实测 252B 字符串同症状），而设备名（12B UTF-8）等小值一直可靠。
    // 内置 nimble_bond 密钥（每条 >40B）从未落盘也是同一根因。
    // 对策：base64 后切成 ≤12 字符的小块分键存储，全部走实测可靠的单条目路径。
    {
        const int CH = 12;
        int n = (int) ((s.length() + CH - 1) / CH);
        Preferences p;
        esp_err_t eo = p.begin("ttbond", false) ? ESP_OK : ESP_FAIL;
        int wok = 0;
        if (eo == ESP_OK) {
            p.putUChar("n", (uint8_t) n);
            p.putUInt("sz", (uint32_t) s.length());
            for (int i = 0; i < n; i++) {
                String key = "c" + String(i);
                String piece = s.substring(i * CH, i * CH + CH);
                if (p.putString(key.c_str(), piece) == (size_t) piece.length()) wok++;
            }
            p.end();
        }
        // 立即读回校验（独立只读句柄，重新拼串比对）
        int rok = -1;
        nvs_handle_t h;
        if (nvs_open("ttbond", NVS_READONLY, &h) == ESP_OK) {
            uint8_t nn = 0;
            rok = 0;
            if (nvs_get_u8(h, "n", &nn) == ESP_OK && nn == n) {
                String re;
                bool ok2 = true;
                for (int i = 0; i < n && ok2; i++) {
                    size_t sl = 0;
                    char k[8];
                    snprintf(k, sizeof(k), "c%d", i);
                    if (nvs_get_str(h, k, NULL, &sl) == ESP_OK && sl > 0 && sl <= CH + 1) {
                        char buf[CH + 1];
                        if (nvs_get_str(h, k, buf, &sl) == ESP_OK) re += buf;
                        else ok2 = false;
                    } else ok2 = false;
                }
                rok = (ok2 && re == s) ? n : -1;
            }
            nvs_close(h);
        }
        char line[TRACE_LINE_LEN];
        snprintf(line, sizeof(line), "[%lus] bkp nvs ch=%d/%d rb=%d/%d open=0x%x\n",
                 (unsigned long) (millis() / 1000), wok, n,
                 rok < 0 ? 0 : rok, n, (unsigned) eo);
        traceAppend(line);
        if (eo == ESP_OK && wok == n && rok == n) ok = true;
    }
    // 介质 2：SPIFFS（完全绕开 NVS）。任何一路成功即可恢复
    {
        bool mounted = SPIFFS.begin(false);
        if (!mounted) mounted = SPIFFS.begin(true);   // 挂不上就格式化
        size_t w = 0;
        if (mounted) {
            File f = SPIFFS.open("/bkp.bin", FILE_WRITE);
            if (f) { w = f.write(blob.data(), len); f.close(); }
        }
        if (w == len) ok = true;
        char line[TRACE_LINE_LEN];
        snprintf(line, sizeof(line), "[%lus] bkp fs w=%d/%d mounted=%d\n",
                 (unsigned long) (millis() / 1000), (int) w, (int) len, (int) mounted);
        traceAppend(line);
    }
    if (ok) bkpWrittenOnce = true;
    return ok;
}

static void bondBackupSaveNow(const char *why) {
    BondBackup b;
    if (!bondBackupCollect(b)) return;   // store 空，没东西可备
    if (bondBackupWrite(b)) {
        char line[TRACE_LINE_LEN];
        snprintf(line, sizeof(line), "[%lus] bond bkp(%s) own=%d peer=%d cccd=%d\n",
                 (unsigned long) (millis() / 1000), why,
                 (int) b.own.size(), (int) b.peer.size(), (int) b.cccd.size());
        traceAppend(line);
    }
}

// 依次尝试 NVS（base64 字符串）/ SPIFFS 读回备份 blob；都失败返回空
static std::vector<uint8_t> bondBackupLoad(const char **src) {
    std::vector<uint8_t> blob;
    {
        Preferences p;
        if (p.begin("ttbond", true)) {
            uint8_t n = p.getUChar("n", 0);
            uint32_t sz = p.getUInt("sz", 0);
            if (n > 0 && n <= 64 && sz > 12 && sz < 2048) {
                String re;
                bool ok2 = true;
                for (int i = 0; i < n && ok2; i++) {
                    String piece = p.getString(("c" + String(i)).c_str(), "");
                    if (piece.length() > 0 && piece.length() <= 12) re += piece;
                    else ok2 = false;
                }
                if (ok2 && (int) re.length() == (int) sz) {
                    unsigned char raw[512];
                    size_t olen = 0;
                    if (mbedtls_base64_decode(raw, sizeof(raw), &olen,
                                              (const unsigned char *) re.c_str(), re.length()) == 0
                        && olen >= 12) {
                        blob.assign(raw, raw + olen);
                        *src = "nvs";
                    }
                }
            }
            p.end();
        }
    }
    if (blob.empty()) {
        if (SPIFFS.begin(false)) {
            File f = SPIFFS.open("/bkp.bin", FILE_READ);
            if (f && f.size() >= 12) {
                blob.resize(f.size());
                f.read(blob.data(), blob.size());
                *src = "fs";
            }
            if (f) f.close();
        }
    }
    if (!blob.empty()) {
        uint32_t mg;
        memcpy(&mg, blob.data(), 4);
        if (mg != BB_MAGIC) blob.clear();
    }
    return blob;
}

static void bondBackupRestore() {
    // 协议栈里已有密钥 → 内置层正常，不动它
    if (bondCount(BLE_STORE_OBJ_TYPE_OUR_SEC) > 0 ||
        bondCount(BLE_STORE_OBJ_TYPE_PEER_SEC) > 0) return;
    const char *src = "?";
    std::vector<uint8_t> blob = bondBackupLoad(&src);
    if (blob.empty()) return;
    uint16_t ver, nOwn, nPeer, nCccd;
    const uint8_t *q = blob.data() + 4;
    memcpy(&ver, q, 2); q += 2;
    if (ver != BB_VER) return;
    memcpy(&nOwn, q, 2); q += 2;
    memcpy(&nPeer, q, 2); q += 2;
    memcpy(&nCccd, q, 2); q += 2;
    // 防御：条目数异常（旧版本写坏的数据）直接放弃
    if (nOwn > 8 || nPeer > 8 || nCccd > 16 ||
        blob.size() < 12 + (size_t) nOwn * sizeof(ble_store_value_sec)
                    + (size_t) nPeer * sizeof(ble_store_value_sec)
                    + (size_t) nCccd * sizeof(ble_store_value_cccd)) {
        char line[TRACE_LINE_LEN];
        snprintf(line, sizeof(line), "[0s] bond restore skip bad hdr %d/%d/%d\n", nOwn, nPeer, nCccd);
        traceAppend(line);
        return;
    }
    int ro = 0, rp = 0, rc = 0;
    for (uint16_t i = 0; i < nOwn; i++, q += sizeof(ble_store_value_sec)) {
        ble_store_value_sec v;
        memcpy(&v, q, sizeof v);
        // 不看返回值：即使内置 NVS 落盘路径失败，RAM 层注入也已生效，
        // 回连加密用的是 RAM 层数据；最终以注入后的 store 计数为准
        ble_store_write_our_sec(&v);
        ro++;
    }
    for (uint16_t i = 0; i < nPeer; i++, q += sizeof(ble_store_value_sec)) {
        ble_store_value_sec v;
        memcpy(&v, q, sizeof v);
        ble_store_write_peer_sec(&v);
        rp++;
    }
    for (uint16_t i = 0; i < nCccd; i++, q += sizeof(ble_store_value_cccd)) {
        ble_store_value_cccd v;
        memcpy(&v, q, sizeof v);
        ble_store_write_cccd(&v);
        rc++;
    }
    char line[TRACE_LINE_LEN];
    snprintf(line, sizeof(line), "[%lus] bond restore(%s) put=%d/%d/%d -> own=%d peer=%d\n",
             (unsigned long) (millis() / 1000), src, ro, rp, rc,
             bondCount(BLE_STORE_OBJ_TYPE_OUR_SEC),
             bondCount(BLE_STORE_OBJ_TYPE_PEER_SEC));
    traceAppend(line);
}

// v1.5.13: NVS 尺寸阶梯诊断。这台设备的 NVS 跨多条目变长写入静默丢失
// （188B blob / 252B 字符串均写"成功"读回 NOT_FOUND，小值一直正常）。
// 开机用 4..252 字节的字符串逐级写入+独立句柄读回，标定单条目可靠边界。
static void bondLadderTest() {
    static const int sizes[] = { 4, 8, 12, 16, 20, 24, 32, 48, 64, 96, 128, 192, 252 };
    const int N = (int) (sizeof(sizes) / sizeof(sizes[0]));
    {
        Preferences p;
        if (!p.begin("ttbond", false)) {
            traceAppend("[bkp] ladder begin fail\n");
            return;
        }
        for (int i = 0; i < N; i++) {
            String v;
            v.reserve(sizes[i]);
            for (int j = 0; j < sizes[i]; j++) v += (char) ('a' + i);
            p.putString(("t" + String(i)).c_str(), v);
        }
        p.end();
    }
    String res = "bkp ladder";
    {
        Preferences q;
        if (!q.begin("ttbond", true)) {
            traceAppend("[bkp] ladder read fail\n");
            return;
        }
        for (int i = 0; i < N; i++) {
            String v;
            v.reserve(sizes[i]);
            for (int j = 0; j < sizes[i]; j++) v += (char) ('a' + i);
            bool r = q.getString(("t" + String(i)).c_str(), "") == v;
            res += " " + String(sizes[i]) + ":" + (r ? "1" : "0");
        }
        q.end();
    }
    res += "\n";
    traceAppend(res.c_str());
}

// 注册 GAP 监听（host 同步前注册会失败）。成功即代表协议栈就绪，
// 立刻做 bond 恢复 —— 必须赶在手机开机自动回连（开机 ~1s）之前。
static bool gapListenerEnsure() {
    static bool done = false;
    if (done) return true;
    if (ble_gap_event_listener_register(&s_gapListener, gapEventCb, NULL) == 0) {
        done = true;
        bondTrace("bonds boot");
        bondBackupRestore();
    }
    return done;
}

static void pollGapListener() {
    if (gapListenerEnsure()) {
        // v1.5.13: 协议栈就绪后做一次 NVS 尺寸阶梯诊断（每次开机一遍）
        static bool ladderDone = false;
        if (!ladderDone) {
            ladderDone = true;
            bondLadderTest();
        }
    }
}
static void pollBleState() {
    bool now = Keyboard.isConnected();
    if (now == lastBleConnState) return;
    lastBleConnState = now;
    bleEvent(now ? "onConnect" : "onDisconnect");
    if (!now) {
        // v1.5.4: 快广播窗口，回连更快
        fastAdvUntil = millis() + 30000;
        advSlowed = false;
        BLEAdvertising *adv = BLEDevice::getAdvertising();
        if (adv) { adv->setMinInterval(0x20); adv->setMaxInterval(0x30); }   // 20~30ms
        BLEDevice::startAdvertising();
    }
}

// HID 报文统计与状态：/blelog 里用它判断断开是否发生在发送报告之后
uint16_t lastX = 0, lastY = 0;
uint32_t hidSendCount = 0;      // v1.2.9-fix1: 现在真的会自增了
uint32_t hidSkipCount = 0;
uint32_t lastHidSendAt = 0;

// OTA 预期镜像大小，0 表示本次上传无效（未授权/非 .bin/超分区）
size_t otaImageSize = 0;

// ---------------- 指令队列（UDP 与 BLE 共用） ----------------
// v1.2.9-fix1: 原来用 portENTER_CRITICAL 包住 1.5KB memcpy，关中断约 10us，
// 对 C3 这种单核单射频的 BLE 时序是实打实的抖动来源。
// 改用互斥量：不关中断，只有真并发时才让出 CPU。
#define BLECMD_MAX     1500
#define BLECMD_QLEN    8        // 8 槽：手势耗时长，收发两侧都要留余量
#define BLECMD_TIMEOUT 3000UL   // 分包中途超过 3 秒没后续 → 丢弃重来

static uint8_t  cmdPool[BLECMD_QLEN][BLECMD_MAX];
static uint16_t cmdLen[BLECMD_QLEN];
static uint8_t  cmdQHead = 0, cmdQTail = 0;
static SemaphoreHandle_t cmdMux = nullptr;

static bool cmdPush(const uint8_t *data, size_t len) {
    if (len == 0 || len > BLECMD_MAX || cmdMux == nullptr) return false;
    if (xSemaphoreTake(cmdMux, pdMS_TO_TICKS(20)) != pdTRUE) return false;
    uint8_t next = (uint8_t) ((cmdQHead + 1) % BLECMD_QLEN);
    bool ok = (next != cmdQTail);          // 环形队列留一格，靠 next 判满
    if (ok) {
        memcpy(cmdPool[cmdQHead], data, len);
        cmdLen[cmdQHead] = (uint16_t) len;
        cmdQHead = next;
    }
    xSemaphoreGive(cmdMux);
    return ok;
}

static bool cmdPop(uint8_t *out, size_t &outLen) {
    if (cmdMux == nullptr) return false;
    if (xSemaphoreTake(cmdMux, pdMS_TO_TICKS(20)) != pdTRUE) return false;
    bool ok = false;
    if (cmdQTail != cmdQHead) {
        uint16_t n = cmdLen[cmdQTail];
        memcpy(out, cmdPool[cmdQTail], n);
        outLen = n;
        cmdQTail = (uint8_t) ((cmdQTail + 1) % BLECMD_QLEN);
        ok = true;
    }
    xSemaphoreGive(cmdMux);
    return ok;
}

// 执行缓冲：从队列 pop 到这里再处理（避免持锁执行）
static uint8_t execBuf[BLECMD_MAX];
// 收包缓冲：pumpIo 专用。
// 必须和 execBuf 分开 —— handleCommand 执行期间会通过 waitYield → pumpIo 收新包，
// 共用一块缓冲会把「正在执行的指令」当场覆盖掉（这条路径很容易踩，务必保持独立）。
static uint8_t rxBuf[BLECMD_MAX];

// ---------------- HID 触摸 ----------------
// state: bit0 = Tip Switch(按下)，bit1 = In Range
static uint8_t curContact = 1;   // 当前 contact id（每次手势轮换，安卓靠它区分不同触点）
static bool touchActive = false; // 当前是否真的有触点按下（复位时避免发多余抬起帧）
static uint16_t gItvlMs = 15;    // 缓存的连接间隔（ms），用于发送节流下限

// 输出通道是否就绪
static bool hidOutReady() {
    return Keyboard.isConnected();
}

// v1.2.9-fix1: 前向声明（touchSend 要用 waitYield，waitYield 定义在文件后半段）
static void waitYield(uint32_t ms);

// 像素 → HID 坐标（描述符 Logical Maximum = 32767，与旧版官方固件一致）
static inline uint16_t toHid(int px, int screen) {
    if (screen <= 0) return 0;
    long v = (long) px * 32767L / screen;
    if (v < 0) v = 0;
    if (v > 32767) v = 32767;
    return (uint16_t) v;
}

// v1.2.9-fix1: 单击微动用 hx+6 会越过 32767（描述符上限），
// 主机侧可能整帧丢弃，表现为「点屏幕右下角的元素经常没反应」。
static inline uint16_t clampHid(int v) {
    return (uint16_t) (v < 0 ? 0 : (v > 32767 ? 32767 : v));
}

// 发送节流：至少间隔 max(8ms, 连接间隔)。
// 连接间隔长于 8ms 时，按间隔发才能保证每帧落进不同的连接事件，
// 否则会被主机合并成 0ms 触摸而触发防误触。
static inline void hidPace() {
    uint16_t gap = gItvlMs > 8 ? gItvlMs : 8;
    uint32_t since = millis() - lastHidSendAt;
    if (lastHidSendAt != 0 && since < gap) waitYield(gap - since);
}

static inline void touchSend(uint8_t state, uint16_t x, uint16_t y) {
    lastX = x;
    lastY = y;
    touchActive = (state & 0x01) != 0;
    if (!Keyboard.isConnected()) {
        hidSkipCount++;
        if (traceTch) {
            char line[TRACE_LINE_LEN];
            snprintf(line, sizeof(line), "[%lus] SKIP state=%u x=%u y=%u (未连接)",
                     (unsigned long) (millis() / 1000), state, x, y);
            traceAppend(line);
        }
        return;
    }
    hidPace();
    Mouse.send(state, (int16_t) x, (int16_t) y, curContact);
    hidSendCount++;                 // v1.2.9-fix1: 原来漏了这一行，hidSent 恒为 0
    lastHidSendAt = millis();
    if (traceTch) {
        char line[TRACE_LINE_LEN];
        snprintf(line, sizeof(line), "[%lus] TCH state=%u x=%u y=%u cid=%u",
                 (unsigned long) (millis() / 1000), state, x, y, curContact);
        traceAppend(line);
    }
}

// 抬起复位：清除可能残留的按下状态（丢包会让固件以为手指还按着）
// 没有活动触点时不发任何报文——多余的抬起帧会污染下一个手势的时序
void releaseTouch() {
    if (!touchActive) return;
    touchSend(0x00, lastX, lastY);
}

// 心跳时间戳：App 探活打 GET /ping，用它判断链路是否真的活着
static volatile unsigned long lastPingAt = 0;

// 读取当前连接间隔（1.25ms 单位）。0 = 读取失败。
// 注意：这是对 NimBLE host 的只读查询，调用点在 loop 线程，
// 每次指令只调一次（原来在 trace 和 runGesture 里各调一次）。
static uint16_t bleConnItvlRaw() {
    BLEServer *srv = BLEDevice::getServer();
    if (srv == nullptr) return 0;
    ble_gap_conn_desc desc;
    if (ble_gap_conn_find(srv->getConnId(), &desc) != 0) return 0;
    return desc.conn_itvl;
}

static uint16_t bleConnItvlMs() {
    uint16_t raw = bleConnItvlRaw();
    if (raw == 0) return 0;
    return (uint16_t) (((uint32_t) raw * 125) / 100);   // raw * 1.25
}

static uint32_t lastConnReqAt = 0;

// v1.2.9-fix1: 原实现每次手势都 requestConnParams，
// 连点时等于每几百毫秒发一次改参请求，握手开销全压在连接事件上。
// 现在 5 秒内最多请求一次。
static void maybeRequestFastConn() {
    if (gItvlMs <= 40) return;
    if (millis() - lastConnReqAt < 5000) return;
    lastConnReqAt = millis();
    BLEServer *srv = BLEDevice::getServer();
    if (srv) srv->requestConnParams(srv->getConnId(), 16, 24, 0, 400);
}

// 执行一段手势：coords 为扁平像素坐标数组，count 为坐标点数
bool runGesture(const int *coords, int count, int duration, int sw, int sh, uint16_t itvlMs) {
    if (count < 2 || sw <= 0 || sh <= 0) return false;
    if (!hidOutReady()) {
#if FW_VERBOSE
        Serial.println("GESTURE rejected: no HID output");
#endif
        return false;
    }

    long total = duration;
    if (total < 0) total = 0;
    if (total > 60000) total = 60000;

    // 每次手势换一个新 contact id —— 安卓靠它区分不同触点
    curContact = (uint8_t) ((curContact % 126) + 1);

    uint16_t hx = toHid(coords[0], sw), hy = toHid(coords[1], sh);

    gItvlMs = itvlMs > 0 ? itvlMs : gItvlMs;
    maybeRequestFastConn();

    // 拟人序列：清残留 → 悬停 → 按下 → (微动) → 悬停抬起 → 离开
    // 部分国产 ROM 的防误触会丢弃「凭空出现又瞬间消失」的静止触摸，
    // 悬停帧 + 微小位移让触摸看起来像真人手指。
    touchSend(0x00, hx, hy);           // 清残留
    waitYield(10);
    touchSend(0x02, hx, hy);           // 悬停（In Range 未按下）
    waitYield(10);

    bool tap = (count == 2 && coords[0] == coords[2] && coords[1] == coords[3]);
    if (tap) {
        // 单击：按下保持足够时长（≥2 个连接间隔且 ≥80ms），中途加微动
        long hold = total;
        if (hold < 80) hold = 80;
        if (itvlMs > 0 && hold < (long) itvlMs * 2) hold = (long) itvlMs * 2;
        touchSend(0x03, hx, hy);
        waitYield((uint32_t) (hold / 2));
        // 往屏幕内侧偏，避免 hx+6 越过 32767 被主机丢帧
        touchSend(0x03, clampHid((int) hx + 6), clampHid((int) hy + 6));
        waitYield((uint32_t) (hold - hold / 2));
        touchSend(0x03, hx, hy);
    } else {
        // 滑动/拖拽：绝对时间轴驱动
        // v1.2.9-fix1: 原实现 per = total/(count-1)，再叠上 touchSend 里的 8ms 限速，
        // 实际每点固定 8ms —— 64 点传 100ms 会跑成 ~500ms，滑动明显偏慢。
        // 改为按 t0 + total*i/(count-1) 定目标时刻，误差不累积，
        // 真正的总时长 ≈ duration（只受物理下限约束）。
        touchSend(0x03, hx, hy);
        uint32_t t0 = millis();
        int span = count - 1;
        for (int i = 1; i < count; i++) {
            uint32_t target = t0 + (uint32_t) ((uint32_t) total * (uint32_t) i / (uint32_t) span);
            int32_t wait = (int32_t) (target - millis());
            if (wait > 0) waitYield((uint32_t) wait);
            touchSend(0x03, toHid(coords[i * 2], sw), toHid(coords[i * 2 + 1], sh));
        }
    }
    touchSend(0x02, hx, hy);           // 抬起，仍在感应范围
    waitYield(10);
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
        waitYield(25);
        Keyboard.write(none);
        if (i + 1 < times) waitYield((uint32_t) (intervalMs < 0 ? 0 : intervalMs));
    }
}

// Keyboard Page 普通键
static void tapKey(uint8_t code, int times = 1, int intervalMs = 60) {
    for (int i = 0; i < times; i++) {
        Keyboard.press(code);
        waitYield(30);
        Keyboard.release(code);
        if (i + 1 < times) waitYield((uint32_t) (intervalMs < 0 ? 0 : intervalMs));
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
// v1.2.9-fix1: 原实现把 UTF-8 字节直接塞进 press()，而 BleComboKeyboard::press 的分支是
//   k >= 136  → 当作非打印键（k-136）
//   k >= 128  → 当作修饰键，直接置 Ctrl/Shift/Alt/Gui 位！
// 汉字字节 0xE4~0xE9 会打出小键盘数字，UTF-8 续字节 0x80~0x87 会误按修饰键，
// 在浏览器/输入法里可能触发快捷键。这里显式拒绝，宁可返回失败让 App 提示。
bool sendText(const String &text, int intervalMs) {
    if (!hidOutReady()) return false;
    if (text.isEmpty()) return true;
    int iv = intervalMs < 0 ? 0 : (intervalMs > 500 ? 500 : intervalMs);
    for (size_t i = 0; i < text.length(); i++) {
        uint8_t c = (uint8_t) text[i];
        if (c > 0x7E) {
            lastError = String("TEXT rejected: non-ASCII byte 0x") + String(c, HEX);
#if FW_VERBOSE
            Serial.println(lastError);
#endif
            return false;
        }
        if (c < 0x20 && c != '\t') continue;   // 控制字符（含 \r\n 之外的）直接跳过
        Keyboard.write(c);
        if (iv > 0) waitYield((uint32_t) iv);
    }
    return true;
}

// ---------------- v1.5.1/1.5.2 drag 滚轮兜底 ----------------
// 用 digitizer 触摸通道模拟「手指滑动」，在 HID 滚轮不生效的主机上是保底方案。
// 方向映射（与滚轮语义对齐）：
//   纵向 d>0（滚轮上滚 = 回看上方内容）→ 手指向下滑（内容下移）
//   横向 h>0（滚轮右滚 = 查看右侧内容）→ 手指向左滑（内容左移）
// 每格一次完整滑动：从中心反方向半格处按下，滑到正方向半格处抬起。
// v1.5.2: cx/cy 可由指令指定（滑动区域中心，像素）；<0 或越界回退屏幕中心。
static void wheelDrag(int d, int h, int n, int cx, int cy, int stepOv) {
    uint16_t sw = scrWFix ? scrWFix : gScrW;
    uint16_t sh = scrHFix ? scrHFix : gScrH;
    if (sw == 0 || sh == 0) {
        lastError = "Wheel drag: no screen size (先发一次手势或网页设置屏幕)";
#if FW_VERBOSE
        Serial.println(lastError);
#endif
        return;
    }
    // v1.5.3: 滚动距离可按指令覆盖（s 字段 20-500），缺省用网页设置的 wheelStep
    int step = (stepOv >= 20 && stepOv <= 500) ? stepOv : (wheelStep > 0 ? wheelStep : 120);
    long dx = -(long) h * step;
    long dy = (long) d * step;
    if (dx == 0 && dy == 0) return;
    if (cx < 0 || cx >= (int) sw) cx = sw / 2;
    if (cy < 0 || cy >= (int) sh) cy = sh / 2;

    curContact = (uint8_t) ((curContact % 126) + 1);
    // 起终点 = 中心 ∓ 半格；逐端 clamp 进屏幕（留 2px 边距），位置贴边时截短滑动距离
    long sx = cx - dx / 2, sy = cy - dy / 2;
    long ex = sx + dx, ey = sy + dy;
    if (sx < 2) { ex -= (2 - sx); sx = 2; }
    if (sy < 2) { ey -= (2 - sy); sy = 2; }
    if (sx > sw - 2) { ex -= (sx - (sw - 2)); sx = sw - 2; }
    if (sy > sh - 2) { ey -= (sy - (sh - 2)); sy = sh - 2; }
    if (ex < 2) ex = 2;
    if (ey < 2) ey = 2;
    if (ex > sw - 2) ex = sw - 2;
    if (ey > sh - 2) ey = sh - 2;

    for (int k = 0; k < n; k++) {
        touchSend(0x02, toHid((int) sx, sw), toHid((int) sy, sh));   // 悬停进入
        waitYield(10);
        touchSend(0x03, toHid((int) sx, sw), toHid((int) sy, sh));   // 按下
        for (int i = 1; i <= 4; i++) {                               // 4 段插值滑动
            waitYield(15);
            touchSend(0x03, toHid((int) (sx + (ex - sx) * i / 4), sw),
                            toHid((int) (sy + (ey - sy) * i / 4), sh));
        }
        touchSend(0x02, toHid((int) ex, sw), toHid((int) ey, sh));   // 抬起
        waitYield(10);
        touchSend(0x00, toHid((int) ex, sw), toHid((int) ey, sh));   // 离开
        if (k < n - 1) waitYield(40);
    }
}

// ---------------- 指令分发（UDP 与 BLE GATT 共用）----------------
// 包格式：第 1 字节命令字，后面是 payload：
//   0x06 手势 / 0x07 按键 / 0x08 打字 / 0x09 抬起复位 / 0x0A 滚轮(v1.5.0)
//
// v1.2.9-fix1: DynamicJsonDocument 改全局常驻，避免每次指令在堆上反复 4KB 分配
// （C3 只有约 150KB 可用堆，点击一多就是碎片化）。
// 容量核对：64 点 = 128 个数组元素 × 16 字节 ≈ 2KB，4096 有充足余量。
static DynamicJsonDocument docGesture(4096);
static DynamicJsonDocument docKey(512);
static DynamicJsonDocument docText(1536);
static DynamicJsonDocument docWheel(256);

void handleCommand(uint8_t *buf, size_t len) {
    if (len < 2) return;
    uint8_t cmd = buf[0];

    const char *payload = (const char *) (buf + 1);
    size_t payloadLen = len - 1;

#if FW_VERBOSE
    Serial.printf("CMD 0x%02X length=%u connected=%d\n", cmd, (unsigned) len,
                  Keyboard.isConnected() ? 1 : 0);
#endif
    {
        char line[TRACE_LINE_LEN];
        snprintf(line, sizeof(line), "[%lus] CMD 0x%02X len=%u ble=%d",
                 (unsigned long) (millis() / 1000), cmd, (unsigned) len,
                 Keyboard.isConnected() ? 1 : 0);
        traceAppend(line);
    }

    switch (cmd) {
        case 0x06: {   // 手势
            docGesture.clear();
            DeserializationError err = deserializeJson(docGesture, payload, payloadLen);
            if (err) {
                lastError = String("Invalid gesture JSON: ") + err.c_str();
#if FW_VERBOSE
                Serial.println(lastError);
#endif
                return;
            }
            JsonArray a = docGesture["a"];
            if (a.isNull() || a.size() < 4) {
#if FW_VERBOSE
                Serial.println("Gesture ignored: invalid path");
#endif
                return;
            }
            // v1.2.9-fix1: 奇数长度说明坐标被截断，宁可拒绝也别发出半条路径
            if ((a.size() & 1) != 0) {
                lastError = "Gesture ignored: odd coord count";
                return;
            }
            int count = a.size() / 2;
            if (count > 64) count = 64;   // 限制单帧点数，防内存爆
            int duration = docGesture["b"] | 300;
            int sw = docGesture["c"] | 0;
            int sh = docGesture["d"] | 0;
            if (sw <= 0 || sh <= 0) {
#if FW_VERBOSE
                Serial.println("Gesture ignored: invalid screen size");
#endif
                return;
            }
            int coords[128];
            for (int i = 0; i < count * 2; i++) coords[i] = a[i].as<int>();

            gScrW = (uint16_t) sw;   // v1.5.1: 缓存屏幕尺寸给 drag 滚轮模式用
            gScrH = (uint16_t) sh;
            // v1.5.3: 缓存尺寸落 NVS，重启后 drag 模式直接可用（不再 scr=0x0）
            if (prefs.getUShort("gscrw") != gScrW || prefs.getUShort("gscrh") != gScrH) {
                prefs.putUShort("gscrw", gScrW);
                prefs.putUShort("gscrh", gScrH);
            }

            uint16_t itvlMs = bleConnItvlMs();   // 每次指令只查一次
            {
                char line[TRACE_LINE_LEN];
                snprintf(line, sizeof(line), "[%lus] GESTURE n=%d dur=%d screen=%dx%d itvl=%ums",
                         (unsigned long) (millis() / 1000), count, duration, sw, sh,
                         (unsigned) itvlMs);
                traceAppend(line);
            }

            bool ok = runGesture(coords, count, duration, sw, sh, itvlMs);
#if FW_VERBOSE
            Serial.printf("GESTURE %s: coords=%d duration=%d screen=%dx%d\n",
                          ok ? "done" : "rejected", count, duration, sw, sh);
#endif
            break;
        }
        case 0x07: {   // 按键
            docKey.clear();
            if (deserializeJson(docKey, payload, payloadLen)) return;
            String key = docKey["k"] | "";
            int times = docKey["n"] | 1;
            int iv = docKey["i"] | 60;
            if (key.isEmpty()) return;
            bool ok = sendKeyByName(key, times < 1 ? 1 : times, iv);
#if FW_VERBOSE
            Serial.printf("KEY %s x%d -> %d\n", key.c_str(), times, ok ? 1 : 0);
#endif
            if (!ok) lastError = String("Unknown key: ") + key;
            break;
        }
        case 0x08: {   // 键盘打字
            docText.clear();
            if (deserializeJson(docText, payload, payloadLen)) return;
            String text = docText["t"] | "";
            int iv = docText["i"] | 30;
            if (text.isEmpty()) return;
            bool ok = sendText(text, iv);
#if FW_VERBOSE
            Serial.printf("TEXT len=%u -> %d\n", (unsigned) text.length(), ok ? 1 : 0);
#endif
            break;
        }
        case 0x09:     // 抬起复位
            releaseTouch();
            Keyboard.releaseAll();
#if FW_VERBOSE
            Serial.println("RESET done");
#endif
            break;
        case 0x0A: {   // v1.5.0: 鼠标滚轮（纵向 d / 横向 h，n 次每次间隔 i ms）
            docWheel.clear();
            if (deserializeJson(docWheel, payload, payloadLen)) return;
            int d = docWheel["d"] | 0;   // 纵向：正=上滚，负=下滚（HID 惯例）
            int h = docWheel["h"] | 0;   // 横向：正=右滚
            int n = docWheel["n"] | 1;   // 滚动格数，默认 1 格
            int iv = docWheel["i"] | 40; // 每格间隔
            int m = docWheel["m"] | -1;  // v1.5.1: 可选模式覆盖 0=hid 1=drag
            int px = docWheel["x"] | -1; // v1.5.2: drag 滑动区域中心（像素，可省略）
            int py = docWheel["y"] | -1;
            int so = docWheel["s"] | 0;  // v1.5.3: 滚动距离覆盖（每格像素 20-500，0=用网页默认）
            if (d == 0 && h == 0) return;
            if (n < 1) n = 1;
            if (n > 200) n = 200;        // 防一条指令滚屏过度
            if (!Keyboard.isConnected()) {
                lastError = "Wheel ignored: BLE not connected";
                return;
            }
            uint8_t mode = (m == 0 || m == 1) ? (uint8_t) m : wheelMode;
            if (mode == 1) {
                // drag 兜底：走已被验证有效的 digitizer 触摸通道
                wheelDrag(d, h, n, px, py, so);
            } else {
                // HID 原生滚轮：relMouse 报告，单格取值 -127~127
                signed char dd = (signed char) (d > 127 ? 127 : (d < -127 ? -127 : d));
                signed char hh = (signed char) (h > 127 ? 127 : (h < -127 ? -127 : h));
                for (int k = 0; k < n; k++) {
                    Mouse.move(0, 0, dd, hh);
                    if (k < n - 1) waitYield(iv);
                }
            }
#if FW_VERBOSE
            Serial.printf("WHEEL mode=%u d=%d h=%d n=%d @%d,%d rel=%lu/%lu\n", mode,
                          d, h, n, px, py, (unsigned long) relNtfyOk, (unsigned long) relNtfyFail);
#endif
            {
                char line[TRACE_LINE_LEN];
                snprintf(line, sizeof(line), "[%lus] WHEEL %s d=%d h=%d n=%d @%d,%d rel=%lu/%lu",
                         (unsigned long) (millis() / 1000),
                         mode == 1 ? "drag" : "hid", d, h, n, px, py,
                         (unsigned long) relNtfyOk, (unsigned long) relNtfyFail);
                traceAppend(line);
            }
            break;
        }
        default:
#if FW_VERBOSE
            Serial.printf("Unknown command: 0x%02X\n", cmd);
#endif
            break;
    }
}

// ---------------- IO 泵：HTTP + UDP 收包 ----------------
// v1.2.9-fix1: 原来 loop 里 pollUdp 每次只读一个包，而且 runGesture 一跑几百毫秒，
// 期间 server.handleClient() 完全不执行 —— /ping 超时 → App 判定离线 → 回落无障碍，
// UDP 收包缓冲溢出 → 后续点击静默丢失。
// 现在把所有「等待」都收敛到 waitYield → pumpIo，只做 IO 不做执行（避免 handleCommand 递归）。
void pumpIo() {
    if (!ioReady) return;
    server.handleClient();
    // 放在这里而不是只放 loop：手势执行期间也会走到 pumpIo，
    // 断线能第一时间记账并重开广播，不用等手势跑完。
    pollBleState();

    // 一次读空：UDP 是数据报，堆多个包说明上游比我们快，必须尽快取走
    for (;;) {
        int size = udp.parsePacket();
        if (size <= 0) break;
        // 来源白名单：开启后非指定 IP 的包直接丢弃（含 flush，防止占满接收缓冲）
        if (udpWlOn && udp.remoteIP().toString() != udpWlIp) {
            udp.flush();
            udpWlDropCount++;
            continue;
        }
        if (size > BLECMD_MAX) { udp.flush(); udpDropCount++; continue; }
        int len = udp.read(rxBuf, BLECMD_MAX);
        if (len <= 0) break;
        udpCount++;
        if (!cmdPush(rxBuf, (size_t) len)) udpDropCount++;
    }
}

// 所有 delay 的替代品：等待期间保持 HTTP 可响应、UDP 不丢包
static void waitYield(uint32_t ms) {
    uint32_t end = millis() + ms;
    // 短等待不折腾 IO，省开销（HID 帧间隔大多是几 ms）
    if (ms <= 2) {
        delay(ms);
        return;
    }
    while ((int32_t) (end - millis()) > 0) {
        pumpIo();
        delay(1);
    }
}

// ---------------- BLE GATT 指令通道 ----------------
// 复用库里的自定义服务 4fafc201-... 特征 beb5483e-...（READ|WRITE|WRITE_NR）。
// 收包在 NimBLE 协议栈线程，执行在 loop 线程 ——
// 手势要跑几百毫秒，绝不能堵住蓝牙协议栈线程。
//
// App 侧分包协议（JSON 超过一条写的内容时用）：
//   单包：[cmd][payload...]                        —— 直接入队
//   分包：首包 [0xFE][总长lo][总长hi][chunk...]，
//         后续包 [chunk...]，攒够总长后整体入队
// 建议连接后先 requestMtu(247)，单包即可覆盖大部分手势。
static uint8_t bleRxBuf[BLECMD_MAX];
static uint16_t bleRxLen = 0, bleRxTotal = 0;
static uint32_t bleRxStartMs = 0;

class BleCmdCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *c) override {
        // v1.2.9-fix1: 原来 String v = c->getValue() 在协议栈线程里做堆分配，
        // 而文件头注释自己就写了「String 跨线程扩容/释放会撞出堆损坏」。
        // 直接读裸指针，零分配。
        uint8_t *p = c->getData();
        size_t n = c->getLength();
        if (p == nullptr || n == 0) return;

        if (traceTch) {
            char line[TRACE_LINE_LEN];
            int off = snprintf(line, sizeof(line), "[%lus] RX n=%u:", (unsigned long) (millis() / 1000), (unsigned) n);
            for (size_t i = 0; i < n && i < 16 && off < (int) sizeof(line) - 4; i++) {
                off += snprintf(line + off, sizeof(line) - off, " %02X", p[i]);
            }
            traceAppend(line);
        }

        if (p[0] == 0xFE) {                       // 分包首包
            if (n < 3) return;
            bleRxTotal = (uint16_t) (p[1] | (p[2] << 8));
            if (bleRxTotal < 2 || bleRxTotal > BLECMD_MAX) { bleRxTotal = 0; return; }
            bleRxLen = 0;
            bleRxStartMs = millis();
            size_t first = n - 3;
            if (first > (size_t) (BLECMD_MAX - bleRxLen)) first = BLECMD_MAX - bleRxLen;
            memcpy(bleRxBuf + bleRxLen, p + 3, first);
            bleRxLen = (uint16_t) (bleRxLen + first);
        } else if (bleRxTotal > 0) {              // 后续分包
            if (millis() - bleRxStartMs > BLECMD_TIMEOUT) {
                bleRxTotal = 0; bleRxLen = 0;
                bleDropCount++;
                return;
            }
            size_t room = (size_t) bleRxTotal - bleRxLen;
            size_t take = n < room ? n : room;
            memcpy(bleRxBuf + bleRxLen, p, take);
            bleRxLen = (uint16_t) (bleRxLen + take);
        } else {                                  // 单包指令
            if (!cmdPush(p, n)) bleDropCount++;
            return;
        }
        if (bleRxTotal > 0 && bleRxLen >= bleRxTotal) {   // 攒齐了
            if (!cmdPush(bleRxBuf, bleRxLen)) bleDropCount++;
            bleRxTotal = 0;
            bleRxLen = 0;
        }
    }
};

static BleCmdCallbacks bleCmdCallbacks;

// 从队列取一条执行（只在 loop 线程调用，保证 handleCommand 不重入）
void drainCommand() {
    size_t n = 0;
    if (!cmdPop(execBuf, n)) return;
    bleCount++;
    handleCommand(execBuf, n);
}

// ---------------- WiFi ----------------
String apSsid() {
    return deviceName;
}

// v1.2.9-fix1: 拆成「发起」与「等待」两步。
// WiFi.begin() 本身是非阻塞的，射频初始化在它返回时就完成了，
// 所以可以紧接着起 BLE —— 原实现要等连上（最长 15 秒）才建 BLE，
// 路由器重启时用户插电要干等十几秒才能配对。
void beginWifiAsync() {
    String ssid = prefs.getString("ssid", "");
    String pass = prefs.getString("pass", "");

    WiFi.mode(WIFI_STA);
    // 先从原厂固件升级上来时，本固件还没存过配置（hidcfg 为空），
    // 但 WiFi 驱动自己有一份存到 nvs.net80211 的凭据。
    // 用无参 begin() 直接复用，这样刷机后不用重新配网。
    if (!ssid.isEmpty()) {
        WiFi.begin(ssid.c_str(), pass.c_str());
#if FW_VERBOSE
        Serial.printf("WiFi: using local config, ssid=%s\n", ssid.c_str());
#endif
    } else {
        WiFi.begin();
#if FW_VERBOSE
        Serial.println("WiFi: no local config, trying driver-saved credentials");
#endif
    }
}

// 等待连接。期间 loop 不跑，所以这里只做最小化的 yield。
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

void startAp() {
    WiFi.mode(WIFI_AP);
    WiFi.softAP(apSsid().c_str(), AP_PASSWORD);    // v1.2.9-fix1: WiFi.mode(WIFI_AP) 会把省电设置恢复默认，
    // 这里补一次，否则配网期间 BLE 连接间隔会被省电拉长、点击发飘。
    WiFi.setSleep(false);
#if FW_VERBOSE
    Serial.printf("AP started: %s  IP=%s\n", apSsid().c_str(),
                  WiFi.softAPIP().toString().c_str());
#endif
}

// v1.5.4: 热点常开（AP+STA 共存）。连着路由器的同时保持热点，换网络不用刷机救。
// 注意：WiFi.getMode() 判断一律用按位与，AP_STA 模式下 mode == WIFI_STA 不再成立。
void applyApSta() {
    bool staConnected = (WiFi.status() == WL_CONNECTED);
    if (apStaOn && staConnected && !apStaApplied) {
        WiFi.mode(WIFI_AP_STA);
        WiFi.softAP(apSsid().c_str(), AP_PASSWORD);
        WiFi.setSleep(false);
        apStaApplied = true;
        char line[TRACE_LINE_LEN];
        snprintf(line, sizeof(line), "[%lus] AP-STA on ip=192.168.4.1",
                 (unsigned long) (millis() / 1000));
        traceAppend(line);
    } else if ((!apStaOn || !staConnected) && apStaApplied) {
        WiFi.softAPdisconnect(true);
        WiFi.mode(WIFI_STA);
        apStaApplied = false;
    }
}

// v1.4.0: mDNS——浏览器直接访问 http://touchtool.local，不用再查设备 IP。
// 主机名固定 "touchtool"（mDNS 主机名只允许 ASCII，设备名可能是中文不能用）；
// 实例名用自定义设备名，手机上显示更友好。STA 和 AP 模式下都有效。
static const char *MDNS_HOST = "touchtool";

void setupMdns() {
    if (!MDNS.begin(MDNS_HOST)) {
        Serial.println("[mdns] failed to start");
        return;
    }
    MDNS.setInstanceName(deviceName.c_str());
    MDNS.addService("http", "tcp", 80);
#if FW_VERBOSE
    Serial.printf("[mdns] http://%s.local\n", MDNS_HOST);
#endif
}

// ---------------- HTTP 页面 ----------------
String htmlEscape(const String &s) {
    String out = s;
    out.replace("&", "&amp;");
    out.replace("<", "&lt;");
    out.replace(">", "&gt;");
    out.replace("\"", "&quot;");
    // 页面属性统一用单引号包裹（value='...'），单引号也必须转义，
    // 否则 SSID/设备名里带 ' 会把表单结构破坏
    out.replace("'", "&#39;");
    return out;
}

// URL 路径编码（/scan 列表里的 SSID 链接用，中文和空格必须编码）
String urlEncode(const String &s) {
    String out;
    char buf[8];
    for (size_t i = 0; i < s.length(); i++) {
        unsigned char c = (unsigned char) s[i];
        if (isalnum(c) || c == '-' || c == '_' || c == '.' || c == '~') out += (char) c;
        else { snprintf(buf, sizeof(buf), "%%%02X", c); out += buf; }
    }
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
    String html = pageHeader("AutoJS HID");
    html += "<div class=row><span>固件版本</span><span>" FW_VERSION "</span></div>";
    html += "<div class=row><span>芯片</span><span>" + chipName + "</span></div>";
    // 显示 SSID：从原厂固件升级上来时，用它确认沿用的是不是原来那个 WiFi
    html += "<div class=row><span>WiFi</span><span class='" + String(sta ? "ok'>" : "bad'>")
            + (sta ? htmlEscape(WiFi.SSID()) : String("热点模式")) + "</span></div>";
    html += "<div class=row><span>本机 IP</span><span>" + ip + "</span></div>";
    html += "<div class=row><span>域名访问</span><span>touchtool.local</span></div>";
    html += "<div class=row><span>信号强度</span><span>" + (sta ? String(WiFi.RSSI()) + " dBm" : String("-")) + "</span></div>";
    html += "<div class=row><span>蓝牙 HID</span><span class='" + String(Keyboard.isConnected() ? "ok'>已连接" : "bad'>未连接") + "</span></div>";
    html += "<div class=row><span>手势 / UDP / BLE</span><span>" + String(gestureCount) + " / " + String(udpCount) + " / " + String(bleCount) + "</span></div>";
    // 崩溃自保护状态
    html += "<div class=row><span>固件槽位</span><span>" + String(esp_ota_get_running_partition()->label)
            + " · 自保护" + (healthConfirmed ? "已确认" : "观察中") + "</span></div>";
    // 运行状态增强
    uint32_t up = (uint32_t) (millis() / 1000);
    String upStr;
    if (up >= 86400)      upStr = String(up / 86400) + "天" + String((up % 86400) / 3600) + "小时";
    else if (up >= 3600)  upStr = String(up / 3600) + "小时" + String((up % 3600) / 60) + "分";
    else if (up >= 60)    upStr = String(up / 60) + "分" + String(up % 60) + "秒";
    else                  upStr = String(up) + "秒";
    html += "<div class=row><span>运行时长</span><span>" + upStr + "</span></div>";
    html += "<div class=row><span>空闲内存</span><span>" + String(ESP.getFreeHeap() / 1024)
            + " KB（历史最低 " + String(ESP.getMinFreeHeap() / 1024) + " KB）</span></div>";
    html += "<div class=row><span>上次重启原因</span><span>"
            + String(resetReasonName((uint8_t) esp_reset_reason())) + "</span></div>";
    if (udpWlOn) {
        html += "<div class=row><span>UDP 白名单</span><span class=ok>开启 · 仅 " + htmlEscape(udpWlIp) + "</span></div>";
        if (udpWlDropCount) {
            html += "<div class=row><span>白名单拦截</span><span class=bad>" + String(udpWlDropCount) + " 包</span></div>";
        }
    }
    if (!lastError.isEmpty()) {
        html += "<div class=row><span>最近错误</span><span class=bad>" + htmlEscape(lastError) + "</span></div>";
    }

    // 配网
    html += "<h3>WiFi 配网</h3><form method=post action=/wifi>";
    String preSsid = server.arg("ssid");          // 从 /scan 点名称跳转过来时自动填入
    html += "<input name=ssid maxlength=32 required placeholder='WiFi 名称' value='" + htmlEscape(preSsid) + "'>";
    html += "<input name=pass maxlength=64 type=password placeholder='密码（开放网络留空）'>";
    html += "<button type=submit>保存并重启</button></form>";
    html += "<p class=muted><a href=/scan>扫描周边 WiFi</a> · 保存后设备会重启并连接该 WiFi；"
            "连不上会自动回到热点模式（热点密码 " AP_PASSWORD "）</p>";

    // 设备名（蓝牙 + 热点共用）
    html += "<h3>设备名</h3><form method=post action=/name>";
    html += "<input name=name maxlength=16 required placeholder='蓝牙/热点名' value='" + htmlEscape(deviceName) + "'>";
    html += "<button type=submit>保存并重启</button></form>";
    html += "<p class=muted>蓝牙名与热点名一起改；手机上已配对的记录可能要重新配对一次</p>";

    // UDP 来源白名单
    html += "<h3>UDP 来源白名单</h3><form method=post action=/udpwl>";
    html += "<input name=ip maxlength=15 placeholder='手机 IP，如 192.168.1.23' value='" + htmlEscape(udpWlIp) + "'>";
    html += "<button type=submit name=on value=1>开启</button>"
            "<button type=submit name=on value=0 style='background:#6b7280'>关闭</button></form>";
    html += "<p class=muted>开启后仅该 IP 的指令被接受，防同 WiFi 下他人遥控点击；"
            "手机换网络后 IP 会变，需回来更新。立即生效，无需重启。</p>";

    // v1.5.4: 热点常开
    html += "<h3>热点常开</h3><form method=post action=/apsta>";
    html += "<button type=submit name=on value=1>开启</button>"
            "<button type=submit name=on value=0 style='background:#6b7280'>关闭</button></form>";
    html += "<p class=muted>开启后连上路由器时热点也保持（AP+STA 共存），"
            "换 WiFi 或设备失联时随时可连 192.168.4.1 救急改配置。默认关闭，立即生效。</p>";

    // v1.5.1: 滚轮模式
    html += "<h3>滚轮模式</h3><form method=post action=/wheelmode>";
    html += "<div class=row><span>当前</span><span>"
            + String(wheelMode ? "drag（触摸滑动兜底）" : "hid（HID 原生滚轮）") + "</span></div>";
    html += "<input name=step type=number min=20 max=500 placeholder='每格像素(默认120)' value='"
            + String(wheelStep) + "'>";
    html += "<input name=w type=number min=0 max=10000 placeholder='屏宽px(0=自动)' value='"
            + String(scrWFix) + "'>";
    html += "<input name=h2 type=number min=0 max=10000 placeholder='屏高px(0=自动)' value='"
            + String(scrHFix) + "'>";
    html += "<button type=submit name=mode value=0>用 HID 滚轮</button>"
            "<button type=submit name=mode value=1 style='background:#6b7280'>用触摸滑动兜底</button></form>";
    html += "<p class=muted>hid 模式走标准 HID 滚轮，部分安卓主机不响应（看 /blelog 里 rel/sub 诊断）；"
            "drag 模式用屏幕中心的真实滑动模拟滚动，任何手机都生效，但屏幕上会出现一次滑动。"
            "立即生效，无需重启。</p>";

    // OTA
    html += "<h3>固件升级</h3><form method=post action=/update enctype=multipart/form-data>";
    html += "<input type=file name=firmware accept=.bin required>";
    html += "<button type=submit>上传并升级</button></form>";
    html += "<p class=muted>只能刷 app 镜像（文件名带 app 的 .bin，≤1.9MB）；"
            "完整 merged 镜像和 C3 版会被拒收。上传期间不要断电，完成后设备自动重启</p>";
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

void handleScan() {
    // 阻塞式扫描约 2-3 秒；STA 连接时射频会短暂跳信道，网页侧表现为慢一下
    int n = WiFi.scanNetworks();
    String html = pageHeader("周边 WiFi");
    if (n <= 0) {
        html += "<p class=muted>没扫到网络。射频忙时扫描可能失败，<a href=/scan>点此重试</a></p>";
    } else {
        for (int i = 0; i < n; i++) {
            String ssid = WiFi.SSID(i);
            bool enc = WiFi.encryptionType(i) != WIFI_AUTH_OPEN;
            html += "<div class=row><span><a href=/?ssid=" + urlEncode(ssid) + ">"
                    + (ssid.isEmpty() ? String("(隐藏网络)") : htmlEscape(ssid)) + "</a>"
                    + (enc ? " &#128274;" : "") + "</span><span class=muted>"
                    + String(WiFi.RSSI(i)) + " dBm</span></div>";
        }
    }
    WiFi.scanDelete();
    html += "<p class=muted>点击名称回到配置页并自动填入；&#128274; 表示需要密码</p>";
    html += "<p><a href=/>返回</a></p></body>";
    server.send(200, "text/html; charset=utf-8", html);
}

void handleUdpWl() {
    String on = server.arg("on");
    if (on == "1") {
        String ip = server.arg("ip");
        ip.trim();
        IPAddress t;
        if (ip.isEmpty() || !t.fromString(ip)) {
            server.send(400, "text/plain; charset=utf-8", "IP 格式不对，例如 192.168.1.23");
            return;
        }
        udpWlIp = t.toString();      // 规范化（去掉前导 0 等）
        udpWlOn = true;
        prefs.putUChar("udpwl", 1);
        prefs.putString("udpwlip", udpWlIp);
    } else {
        udpWlOn = false;
        prefs.putUChar("udpwl", 0);
    }
    String html = pageHeader("白名单已更新");
    html += "<p class=ok>" + String(udpWlOn ? "已开启：仅接受 " + htmlEscape(udpWlIp) + " 的指令"
                                            : "已关闭（所有来源放行）") + "</p>";
    html += "<p class=muted>立即生效，无需重启。手机 DHCP 换 IP 后要回来改。</p>";
    html += "<p><a href=/>返回</a></p></body>";
    server.send(200, "text/html; charset=utf-8", html);
}

// v1.5.4: 热点常开开关（AP+STA 共存）
void handleApSta() {
    String on = server.arg("on");
    if (on != "0" && on != "1") {
        server.send(400, "text/plain; charset=utf-8", "on 需为 0 或 1");
        return;
    }
    apStaOn = (on == "1");
    prefs.putUChar("apsta", apStaOn ? 1 : 0);
    applyApSta();
    String html = pageHeader("热点常开已更新");
    html += "<p class=ok>" + String(apStaOn ? "已开启：连着路由器时热点同步保持，"
                                            "192.168.4.1 随时可访问"
                                          : "已关闭：仅断网/未配网时开热点") + "</p>";
    html += "<p class=muted>立即生效，无需重启。开启会增加一点功耗。</p>";
    html += "<p><a href=/>返回</a></p></body>";
    server.send(200, "text/html; charset=utf-8", html);
}

void handleWheelMode() {
    String mode = server.arg("mode");
    long step = server.arg("step").toInt();
    long w = server.arg("w").toInt();
    long hgt = server.arg("h2").toInt();
    if (step >= 20 && step <= 500) wheelStep = (uint16_t) step;
    else if (step != 0) { server.send(400, "text/plain; charset=utf-8", "每格像素需 20-500"); return; }
    if (w >= 0 && w <= 10000) scrWFix = (uint16_t) w;
    if (hgt >= 0 && hgt <= 10000) scrHFix = (uint16_t) hgt;
    if (mode == "1") wheelMode = 1;
    else if (mode == "0") wheelMode = 0;
    else { server.send(400, "text/plain; charset=utf-8", "mode 需为 0 或 1"); return; }
    prefs.putUChar("whlmode", wheelMode);
    prefs.putUShort("whlstep", wheelStep);
    prefs.putUShort("scrw", scrWFix);
    prefs.putUShort("scrh", scrHFix);
    String html = pageHeader("滚轮模式已更新");
    html += "<p class=ok>当前模式：<b>" + String(wheelMode ? "drag（触摸滑动兜底）" : "hid（HID 原生滚轮）")
         + "</b>，每格 " + String(wheelStep) + "px，屏幕 "
         + String(scrWFix ? scrWFix : gScrW) + "x" + String(scrHFix ? scrHFix : gScrH) + "</p>";
    html += "<p class=muted>立即生效，无需重启。测试后看 /blelog 的 wheel=/sub= 诊断行。</p>";
    html += "<p><a href=/>返回</a></p></body>";
    server.send(200, "text/html; charset=utf-8", html);
}

void handleName() {
    String name = server.arg("name");
    name.trim();
    // BLE 广播名上限约 29 字节，热点名同理；16 字节 = 约 5 个汉字，足够区分设备
    if (name.isEmpty() || name.length() > 16) {
        server.send(400, "text/plain; charset=utf-8", "名字需 1-16 字节（一个汉字占 3 字节）");
        return;
    }
    prefs.putString("devname", name);
    String html = pageHeader("设备名已保存");
    html += "<p class=ok>设备名已保存为 <b>" + htmlEscape(name) + "</b>，即将重启生效</p>";
    html += "<p class=muted>蓝牙名和热点名会一起变；手机里已配对的记录可能需要重新配对</p>";
    server.send(200, "text/html; charset=utf-8", html);
    delay(1200);
    ESP.restart();
}

void handleInfo() {
    StaticJsonDocument<640> doc;
    doc["fw"] = FW_VERSION;
    doc["chip"] = chipName;            // v1.2.9-fix1: 不再谎报 C3
    doc["type"] = "hid";
    doc["name"] = deviceName;
    doc["ble"] = Keyboard.isConnected();
    doc["ip"] = WiFi.isConnected() ? WiFi.localIP().toString() : WiFi.softAPIP().toString();
    doc["gestures"] = gestureCount;
    doc["commands"] = udpCount;
    doc["bleCommands"] = bleCount;
    doc["bleDrops"] = bleDropCount;
    doc["udpDrops"] = udpDropCount;
    doc["uptime"] = (uint32_t) (millis() / 1000);
    doc["heap"] = ESP.getFreeHeap();
    doc["udpWl"] = udpWlOn;
    doc["part"] = esp_ota_get_running_partition()->label;
    doc["health"] = healthConfirmed;
    doc["ota"] = true;
    doc["textAscii"] = true;           // 能力位：打字只支持 ASCII，App 可据此提前提示
    doc["cmds"] = "0x06,0x07,0x08,0x09,0x0A";
    doc["wheelMode"] = wheelMode;      // v1.5.1: 0=hid 1=drag，App 可在帮助页提示
    String out;
    serializeJson(doc, out);
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.sendHeader("Cache-Control", "no-store");
    server.send(200, "application/json", out);
}

// 轻量探活：只回一行文本，App 用来判断「在线/离线」和「是不是新固件」
void handlePing() {
    lastPingAt = millis();
    server.sendHeader("Cache-Control", "no-store");
    server.send(200, "text/plain", "pong " FW_VERSION);
}

// 蓝牙排查页：手机连热点后直接打开 /blelog 就能看到事件轨迹，
// 不用插串口也能判断配对断在连接、还是安全握手之后。
// ?tch=1 打开逐帧触摸轨迹（默认关，一次手势几十行，会盖掉其它信息）
void handleBleLog() {
    if (server.hasArg("tch")) traceTch = (server.arg("tch") == "1");
    if (server.hasArg("trace")) traceOn = (server.arg("trace") != "0");

    String out = "fw=" FW_VERSION "\n";
    out += "chip=" + chipName + "\n";
    out += "name=" + deviceName + "\n";
    out += "connected=" + String(Keyboard.isConnected() ? "yes" : "no") + "\n";
    out += "conn=" + String(bleConnectCount) + " dis=" + String(bleDisconnectCount) + "\n";
    out += "uptime=" + String(millis() / 1000) + "s\n";
    out += "hidSent=" + String((unsigned long) hidSendCount)
         + " hidSkipped=" + String((unsigned long) hidSkipCount) + "\n";
    out += "cmdUdp=" + String(udpCount) + " cmdBle=" + String(bleCount)
         + " bleDrops=" + String(bleDropCount) + " udpDrops=" + String(udpDropCount) + "\n";
    out += "freeHeap=" + String(ESP.getFreeHeap()) + "\n";
    out += "resetReason=" + String(resetReasonName((uint8_t) esp_reset_reason()))
         + " (last=" + resetReasonName(prefs.getUChar("lastRst", 0xFF)) + ")\n";
    out += "part=" + String(esp_ota_get_running_partition()->label) + "\n";
    out += "crashCount=" + String(prefs.getUChar("crashct", 0))
         + "/" + String(CRASH_ROLLBACK_THRESHOLD) + "\n";
    out += "rollbacks=" + String(prefs.getUChar("rbct", 0)) + "\n";
    out += "health=" + String(healthConfirmed ? "confirmed" : "pending") + "\n";
    // v1.5.1: 滚轮诊断段 —— mode/step/rel 通道通知成败/报告句柄/订阅数
    out += "wheel=mode:" + String(wheelMode ? "drag" : "hid")
         + " step:" + String(wheelStep)
         + " relOk:" + String((unsigned long) relNtfyOk)
         + " relFail:" + String((unsigned long) relNtfyFail) + "\n";
    out += "scr=" + String(scrWFix ? scrWFix : gScrW) + "x" + String(scrHFix ? scrHFix : gScrH) + "\n";
    {
        // 四个输入报告特征中 abs(触摸)/rel(滚轮) 的句柄必须不同；
        // rel 订阅数为 0 说明主机没订阅这条报告 —— 滚轮通知发也白发。
        BLECharacteristic *ra = Keyboard.inputMouse;
        BLECharacteristic *rr = Keyboard.inputRELMouse;
        out += "hnd abs=" + String(ra ? (unsigned) ra->getHandle() : 0)
             + " rel=" + String(rr ? (unsigned) rr->getHandle() : 0)
             + (ra && rr && ra->getHandle() == rr->getHandle() ? " !HANDLE-CLASH" : "")
             + "\n";
        out += "sub abs=" + String(ra ? (unsigned) ra->getSubCount() : 0)
             + " rel=" + String(rr ? (unsigned) rr->getSubCount() : 0) + "\n";
    }
    out += "trace=" + String(traceOn ? "on" : "off")
         + " tch=" + String(traceTch ? "on" : "off") + "\n";
    // v1.5.8: bond 计数改用 ble_store_util_count（NimBLE 存储层权威数据），
    // nvs_entry_find 枚举在 IDF5 上不可靠（v1.5.5/1.5.6 bond 恒 0 的根源）。
    // own=本机密钥数 peer=手机密钥数；nvs 分区统计保留作对照。
    {
        nvs_stats_t st;
        String stats;
        if (nvs_get_stats("nvs", &st) == ESP_OK) {
            stats = " used:" + String((unsigned) st.used_entries)
                  + " free:" + String((unsigned) st.free_entries)
                  + " ns:" + String((unsigned) st.namespace_count);
        } else {
            stats = " stats-fail";
        }
        int own = bondCount(BLE_STORE_OBJ_TYPE_OUR_SEC);
        int peer = bondCount(BLE_STORE_OBJ_TYPE_PEER_SEC);
        out += "bond=own:" + String(own) + " peer:" + String(peer)
             + ((own > 0 || peer > 0) ? "" : " (none)") + stats + "\n";
        // v1.5.11: 自备份状态（双介质）。
        // none=NVS 和 SPIFFS 都没有；bkp(nvs)/bkp(fs)=来源 + 条目数
        {
            const char *src = "?";
            std::vector<uint8_t> blob = bondBackupLoad(&src);
            String bkp;
            if (blob.empty()) {
                bkp = "bkp=none";
            } else {
                uint16_t v, nO, nP, nC;
                memcpy(&v, blob.data() + 4, 2);
                memcpy(&nO, blob.data() + 6, 2);
                memcpy(&nP, blob.data() + 8, 2);
                memcpy(&nC, blob.data() + 10, 2);
                bkp = "bkp(" + String(src) + ")=own:" + String(nO) + " peer:" + String(nP)
                    + " cccd:" + String(nC) + " bytes:" + String((unsigned) blob.size());
            }
            out += bkp + "\n";
        }
        // 内置持久化层探针：NimBLE-Arduino/IDF 的 bond 存在
        // "nimble_bond" 命名空间的 our_sec_1/peer_sec_1 等 blob 里。
        // store 里 own/peer>0 而这里全 0 → 内置层写失败；反之加载失败。
        {
            nvs_handle_t h;
            String probe;
            if (nvs_open("nimble_bond", NVS_READONLY, &h) == ESP_OK) {
                const char *keys[] = { "our_sec_1", "peer_sec_1", "our_sec", "peer_sec" };
                for (unsigned i = 0; i < 4; i++) {
                    size_t sz = 0;
                    if (nvs_get_blob(h, keys[i], NULL, &sz) == ESP_OK && sz > 0) {
                        probe += " " + String(keys[i]) + ":" + String((unsigned) sz);
                    }
                }
                nvs_close(h);
            }
            out += "nvsbond=" + (probe.isEmpty() ? String("(none)") : probe) + "\n";
        }
    }
    out += "---- trace ----\n";

    // v1.2.9-fix1: 原来是 char snap[2400] 放栈上，WebServer 回调里 2.4KB 栈很危险，
    // 改静态缓冲（只在 loop 线程用，无并发）。
    static char snap[TRACE_LINES * TRACE_LINE_LEN + 1];
    traceDump(snap, sizeof(snap));
    out += snap[0] ? snap : "(empty)\n";
    server.sendHeader("Cache-Control", "no-store");
    server.send(200, "text/plain; charset=utf-8", out);
}

// ---------------- OTA ----------------
String expectedAuthValue() {
    // base64("admin:autojs1234")，与 OTA_USER/OTA_PASS 对应
    return String("Basic ") + "YWRtaW46YXV0b2pzMTIzNA==";
}

bool otaAuthorized() {
    String auth = server.header("Authorization");
    if (auth.length() == 0) {
#if OTA_ALLOW_NO_AUTH
        // 兼容老 App 与浏览器直传，靠 .bin 后缀 + 大小校验兜底
        return true;
#else
        // 注意：这是在 upload 回调里，直接 send 不生效，只能拒绝这次上传
        return false;
#endif
    }
    return auth.equalsIgnoreCase(expectedAuthValue());
}

// v1.2.9-fix1: 原来按固定 40/120 估算 multipart 外壳，boundary 或文件名变长就估小，
// 导致 otaImageSize 偏大 → Update 期望比实际多 → end() 判失败（OTA 失败最难查的一类）。
// 现在从 Content-Type 里取真实 boundary，按 RFC 7578 逐段精确计算。
//   --B\r\n                                            2 + L + 2
//   Content-Disposition: form-data; name="firmware";  60
//                        filename="F"\r\n              F + 1 + 2
//   Content-Type: application/octet-stream\r\n        40
//   \r\n                                              2
//   <data>
//   \r\n--B--\r\n                                      2 + 2 + L + 2 + 2
// 合计 115 + F + 2L
size_t multipartOverhead(size_t filenameLen) {
    String ctype = server.header("Content-Type");
    int idx = ctype.indexOf("boundary=");
    if (idx < 0) return 0;
    String b = ctype.substring(idx + 9);
    if (b.startsWith("\"")) {
        int e = b.indexOf('"', 1);
        if (e > 0) b = b.substring(1, e);
    }
    size_t L = b.length();
    if (L < 8 || L > 70) return 0;      // 不像正常 boundary，放弃精确计算
    return 115 + filenameLen + 2 * L;
}

void handleOtaUpload() {
    HTTPUpload &up = server.upload();
    if (up.status == UPLOAD_FILE_START) {
        if (!otaAuthorized()) {
            Serial.println("OTA rejected: unauthorized");
            return;
        }
        // 文件名必须 .bin（大小写不敏感），避免误传其他文件把分区写坏
        String fn = up.filename;
        fn.toLowerCase();
        if (!fn.endsWith(".bin")) {
            Serial.println("OTA rejected: not .bin");
            return;
        }
#if FW_VERBOSE
        Serial.printf("OTA start: %s\n", up.filename.c_str());
#endif

        // 之前这里用 UPDATE_SIZE_UNKNOWN，是 OTA 一直写失败的直接原因：
        // Update 不知道总量就无法在写入阶段做边界检查。
        // 正确做法是 Content-Length 减去 multipart 头尾开销，交给 Update.begin()。
        // v1.3.1: overhead 只是估算，不能再拿它做字节级判断（1.3.0 的教训：
        // 估算差 1 字节 → end 阶段 totalSize 不等 → 合法文件 100% 被拒）。
        // begin 的 size 只用作“写不越界”的上限，给足余量；
        // 镜像是否完整由 Update.end() 的校验和 + SHA256 决定，那里才是权威。
        size_t total = (size_t) server.clientContentLength();
        size_t overhead = multipartOverhead(up.filename.length());
        if (overhead == 0) overhead = 220;   // 解析不到 boundary，保守估算
        otaImageSize = (total > overhead) ? (total - overhead) : 0;

        const size_t appMax = 0x1E0000;      // 与 partitions.csv 中 app 分区一致
        if (otaImageSize < 0x1000 || otaImageSize > appMax) {
            Serial.println("OTA rejected: bad size");
            otaImageSize = 0;
            return;
        }
        size_t cap = (otaImageSize + 512 > appMax) ? appMax : otaImageSize + 512;
        if (!Update.begin(cap)) {
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
            // v1.3.1: 允许 ±512B 估算偏差（1.3.0 在这里把所有合法上传都判成
            // mismatch）；截断或损坏的镜像会被下面 Update.end() 的校验拦下。
            size_t want = otaImageSize;
            if (up.totalSize + 512 < want || up.totalSize > want + 512) {
                Serial.printf("OTA size mismatch: got %u expected %u\n",
                              (unsigned) up.totalSize, (unsigned) want);
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
// 并把当前 OTA 槽标记为有效。
static void crashGuardConfirmHealth() {
    if (healthConfirmed || millis() < HEALTH_CONFIRM_MS) return;
    healthConfirmed = true;
    prefs.putUChar("crashct", 0);
    esp_ota_mark_app_valid_cancel_rollback();
    Serial.println("[crash-guard] health confirmed (uptime >= 60s)");
}

// ---------------- 初始化 ----------------
void setupBle() {
    // 设备名：网页可自定义（/name），持久化在 nvs；空值回落默认
    deviceName = prefs.getString("devname", "");
    if (deviceName.isEmpty()) deviceName = DEVICE_PREFIX;
    // 想区分多台设备时取消下面一行注释（加 MAC 后两字节），
    // 代价是已配对过的设备名会变，需要重新配对一次。
    // deviceName += "-" + String((uint16_t)(ESP.getEfuseMac() & 0xFFFF), HEX);

    // 名字要在 begin() 之前设置：begin() 会用 deviceName 初始化 BLE 广播
    Keyboard.deviceName = deviceName.c_str();
    Keyboard.deviceManufacturer = "Espressif";
    Keyboard.setCallbacks(&bleCmdCallbacks);
    hidInitDiag();
    Keyboard.begin();
    Mouse.begin();
    // begin() 只是创建任务，BLEDevice::init() 在那个任务里异步执行，
    // 此处不能直接调 startAdvertising()，广播由库内部在初始化完成后开启。
    // v1.5.9: 抢跑等 host 同步（最多 ~6s），一同步立刻恢复 bond 并注册监听。
    // 必须赶在 waitWifi() 的 12s 阻塞之前 —— 手机开机自动回连只要 ~1s。
    for (int i = 0; i < 300 && !gapListenerEnsure(); i++) {
        delay(20);
    }
#if FW_VERBOSE
    Serial.printf("BLE HID started: %s\n", deviceName.c_str());
#endif
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

    // UDP 白名单配置加载（pumpIo 收包时检查）
    udpWlOn = prefs.getUChar("udpwl", 0) != 0;
    udpWlIp = prefs.getString("udpwlip", "");
    // v1.5.4: 热点常开（连接建立后在 loop 里应用）
    apStaOn = prefs.getUChar("apsta", 0) != 0;

    // v1.5.1: 滚轮模式配置（0=hid 1=drag；屏幕尺寸 0=用手势缓存）
    wheelMode = prefs.getUChar("whlmode", 0);
    wheelStep = prefs.getUShort("whlstep", 120);
    scrWFix = prefs.getUShort("scrw", 0);
    scrHFix = prefs.getUShort("scrh", 0);
    // v1.5.3: 手势缓存尺寸也恢复，重启不丢（网页手动值优先级更高，不受影响）
    gScrW = prefs.getUShort("gscrw", 0);
    gScrH = prefs.getUShort("gscrh", 0);

    // 崩溃自保护：越早跑越好，赶在任何可能出问题的初始化之前
    crashGuardBoot();

    // 运行时读取芯片型号（原来写死 C3，S3 版会谎报）
    chipName = String(ESP.getChipModel()) + " rev" + String(ESP.getChipRevision());

    // 指令队列互斥量必须先于任何 push 创建
    cmdMux = xSemaphoreCreateMutex();

    // 关掉 WiFi 省电：C3 单射频，省电模式会让 BLE 的连接间隔抖动得厉害，
    // 表现为 HID 报告延迟/超时、蓝牙频繁掉线。HID 场景吞吐要求极低，不省这个电。
    WiFi.setSleep(false);

    // 顺序很重要：ESP32-C3 只有一套 2.4G 射频，WiFi 必须在 BLE 之前初始化。
    // 反过来的话，WiFi 起来时会重置 BT 控制器，表现就是手机能搜到设备、
    // 点配对转几秒圈然后设备从列表里消失（连接被射频重配打断）。
    //
    // v1.2.9-fix1: 但「初始化」不等于「连上」。WiFi.begin() 返回时射频已就绪，
    // 可以立刻起 BLE，把原来最长 15 秒的配对等待砍掉。
    beginWifiAsync();
    delay(50);          // 让射频先完成一次信道配置，再交给 BLE
    setupBle();

    Keyboard.releaseAll();
    releaseTouch();

    // 等 WiFi 结果（连不上就开热点）。这期间 loop 还没跑，指令会堆在队列里。
    if (!waitWifi(12000)) {
        startAp();
    } else {
#if FW_VERBOSE
        Serial.printf("WiFi connected: %s\n", WiFi.localIP().toString().c_str());
#endif
    }
    setupMdns();

    // HTTP 服务
    server.on("/", HTTP_GET, handleRoot);
    server.on("/wifi", HTTP_POST, handleWifi);
    server.on("/scan", HTTP_GET, handleScan);
    server.on("/udpwl", HTTP_POST, handleUdpWl);
    server.on("/apsta", HTTP_POST, handleApSta);
    server.on("/wheelmode", HTTP_POST, handleWheelMode);
    server.on("/name", HTTP_POST, handleName);
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

    // IO 就绪后 waitYield 才能安全调用 server.handleClient()
    ioReady = true;
}

void loop() {
    pumpIo();                    // HTTP + UDP 收包（只入队，不执行）
    pollBleState();              // 连接状态跳变 → 计数 + 轨迹 + 重开广播
    pollGapListener();           // v1.5.8: 注册 GAP 监听（host 同步后），记录配对/断开事件
    if (bkpReq) {                // v1.5.10: 主线程执行密钥备份（协议栈任务只置标志）
        bkpReq = false;
        bondBackupSaveNow("enc");
    }
    // v1.5.13: 每 10s 巡检备份块是否被抹 —— 抓"写成功读不到"的现行
    {
        static unsigned long lastBkpChk = 0;
        static int lastBkpSz = -1;
        if (bkpWrittenOnce && millis() - lastBkpChk > 10000) {
            lastBkpChk = millis();
            nvs_handle_t h;
            int sz = -1;
            if (nvs_open("ttbond", NVS_READONLY, &h) == ESP_OK) {
                uint8_t nn = 0;
                sz = nvs_get_u8(h, "n", &nn) == ESP_OK ? (int) nn : -1;
                nvs_close(h);
            } else {
                sz = -2;
            }
            if (sz != lastBkpSz) {
                char line[TRACE_LINE_LEN];
                snprintf(line, sizeof(line), "[%lus] bkp chk sz=%d (was %d)\n",
                         (unsigned long) (millis() / 1000), sz, lastBkpSz);
                traceAppend(line);
                lastBkpSz = sz;
            }
        }
    }
    crashGuardConfirmHealth();
    drainCommand();              // 取一条执行（内部 waitYield 会继续 pumpIo）

    // WiFi 断线重连：指数退避（v1.5.4）。路由器重启期间不再无谓空转，
    // 连上后退避复位；热点常开在连上时应用。
    static unsigned long lastCheck = 0;
    if (millis() - lastCheck > 500) {
        lastCheck = millis();
        if (WiFi.getMode() & WIFI_STA) {
            if (WiFi.status() == WL_CONNECTED) {
                wifiBackoffMs = 2000;
                applyApSta();
            } else if (millis() - lastWifiTry >= wifiBackoffMs) {
                lastWifiTry = millis();
                WiFi.reconnect();
                unsigned long next = wifiBackoffMs * 5 / 2;
                wifiBackoffMs = next > 60000 ? 60000 : next;
            }
        }
    }

    // 广播保活退化为兜底：正常情况下 pollBleState 里断开瞬间就重开了（带 30s 快窗口），
    // 这里只在「既没连上、又超过 30 秒」时补一次；快窗口到期自动降速省电。
    static unsigned long lastAdv = 0;
    if (millis() - lastAdv > 30000) {
        lastAdv = millis();
        if (!Keyboard.isConnected()) {
            if (!advSlowed && fastAdvUntil != 0 && millis() > fastAdvUntil) {
                advSlowed = true;
                BLEAdvertising *adv = BLEDevice::getAdvertising();
                if (adv) { adv->setMinInterval(0x100); adv->setMaxInterval(0x140); }   // 100~125ms
            }
            BLEDevice::startAdvertising();
            bleEvent("adv keepalive");
        }
    }
    delay(1);
}
