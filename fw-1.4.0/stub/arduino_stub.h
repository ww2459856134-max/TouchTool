// 语法检查用 stub —— 只用于 g++ -fsyntax-only，不参与真实编译
#pragma once
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <string>

typedef uint8_t byte;

#define HEX 16
#define DEC 10

// ---------------- String ----------------
class String {
public:
    String() {}
    String(const char *) {}
    String(const String &) {}
    String(char) {}
    explicit String(unsigned char, unsigned char = 10) {}
    explicit String(int, unsigned char = 10) {}
    explicit String(unsigned int, unsigned char = 10) {}
    explicit String(long, unsigned char = 10) {}
    explicit String(unsigned long, unsigned char = 10) {}
    explicit String(float) {}
    explicit String(double) {}

    String &operator=(const char *) { return *this; }
    String &operator=(const String &) { return *this; }
    String &operator+=(const char *) { return *this; }
    String &operator+=(const String &) { return *this; }
    String operator+(const char *) const { return *this; }
    String operator+(const String &) const { return *this; }
    bool operator==(const char *) const { return false; }
    bool operator==(const String &) const { return false; }
    bool operator!=(const char *) const { return false; }
    bool equalsIgnoreCase(const String &) const { return false; }

    size_t length() const { return 0; }
    bool isEmpty() const { return true; }
    const char *c_str() const { return ""; }
    int indexOf(const char *) const { return -1; }
    int indexOf(char) const { return -1; }
    int indexOf(char, unsigned int) const { return -1; }
    String substring(int) const { return *this; }
    String substring(int, int) const { return *this; }
    bool startsWith(const char *) const { return false; }
    bool endsWith(const char *) const { return false; }
    void toLowerCase() {}
    void replace(const char *, const char *) {}
    char operator[](int) const { return 0; }
};
inline String operator+(const char *, const String &s) { return s; }

// ---------------- Serial ----------------
class SerialStub {
public:
    void begin(unsigned long) {}
    void begin(unsigned long, int) {}
    void setTxTimeoutMs(unsigned long) {}
    void print(const char *) {}
    void print(const String &) {}
    void print(char) {}
    void print(int) {}
    void print(unsigned) {}
    void print(long) {}
    void println() {}
    void println(const char *) {}
    void println(const String &) {}
    void println(int) {}
    void printf(const char *, ...) {}
};
extern SerialStub Serial;

unsigned long millis();
unsigned long micros();
void delay(unsigned long);
void yield();
#define HIGH 1
#define LOW 0

// ---------------- IPAddress ----------------
class IPAddress {
public:
    String toString() const { return String(); }
};

// ---------------- WiFi ----------------
typedef enum { WL_IDLE_STATUS = 0, WL_NO_SSID_AVAIL = 1, WL_CONNECTED = 3, WL_CONNECT_FAILED = 4 } wl_status_t;
typedef enum { WIFI_OFF = 0, WIFI_STA = 1, WIFI_AP = 2, WIFI_AP_STA = 3 } wifi_mode_t;

class WiFiStub {
public:
    bool mode(wifi_mode_t) { return true; }
    wifi_mode_t getMode() { return WIFI_STA; }
    bool begin() { return true; }
    bool begin(const char *, const char * = nullptr) { return true; }
    wl_status_t status() { return WL_CONNECTED; }
    bool isConnected() { return true; }
    IPAddress localIP() { return IPAddress(); }
    IPAddress softAPIP() { return IPAddress(); }
    bool softAP(const char *, const char * = nullptr) { return true; }
    String SSID() { return String(); }
    int32_t RSSI() { return 0; }
    bool reconnect() { return true; }
    bool setSleep(bool) { return true; }
};
extern WiFiStub WiFi;

class WiFiUDP {
public:
    uint8_t begin(uint16_t) { return 1; }
    int parsePacket() { return 0; }
    int read(uint8_t *, size_t) { return 0; }
    void flush() {}
};
extern WiFiUDP udp;

// ---------------- WebServer ----------------
#define HTTP_GET 1
#define HTTP_POST 2

struct HTTPUpload {
    int status;
    String filename;
    size_t totalSize;
    size_t currentSize;
    uint8_t *buf;
};
#define UPLOAD_FILE_START 0
#define UPLOAD_FILE_WRITE 1
#define UPLOAD_FILE_END 2
#define UPLOAD_FILE_ABORTED 3

class WebServer {
public:
    WebServer(int) {}
    void begin() {}
    void handleClient() {}
    void on(const String &, int, void (*)()) {}
    void on(const String &, int, void (*)(), void (*)()) {}
    void onNotFound(void (*)()) {}
    void send(int, const char *, const String &) {}
    void send(int, const char *, const char *) {}
    void sendHeader(const String &, const String &) {}
    String arg(const String &) { return String(); }
    bool hasArg(const String &) { return false; }
    String header(const String &) { return String(); }
    int clientContentLength() { return 0; }
    HTTPUpload &upload();
};

// ---------------- Update ----------------
class UpdateStub {
public:
    bool begin(size_t) { return true; }
    bool write(uint8_t *, size_t) { return true; }
    bool end(bool) { return true; }
    void abort() {}
    bool isRunning() { return true; }
    bool hasError() { return false; }
    bool isFinished() { return true; }
    void printError(SerialStub &) {}
};
extern UpdateStub Update;

// ---------------- Preferences ----------------
class Preferences {
public:
    bool begin(const char *, bool) { return true; }
    String getString(const char *, const String & = String()) { return String(); }
    uint8_t getUChar(const char *, uint8_t = 0) { return 0; }
    size_t putString(const char *, const String &) { return 0; }
    size_t putUChar(const char *, uint8_t) { return 0; }
};

// ---------------- ESP ----------------
class EspStub {
public:
    const char *getChipModel() { return "ESP32-C3"; }
    uint8_t getChipRevision() { return 3; }
    uint32_t getFreeHeap() { return 100000; }
    uint64_t getEfuseMac() { return 0; }
    void restart() {}
};
extern EspStub ESP;

// ---------------- FreeRTOS / 临界区 ----------------
typedef void *SemaphoreHandle_t;
SemaphoreHandle_t xSemaphoreCreateMutex();
int xSemaphoreTake(SemaphoreHandle_t, uint32_t);
int xSemaphoreGive(SemaphoreHandle_t);
#define pdTRUE 1
#define pdFALSE 0
#define pdMS_TO_TICKS(x) (x)

typedef int portMUX_TYPE;
#define portMUX_INITIALIZER_UNLOCKED 0
void portENTER_CRITICAL(portMUX_TYPE *);
void portEXIT_CRITICAL(portMUX_TYPE *);

// ---------------- esp_ota / esp_system ----------------
struct esp_partition_t { const char *label; };
typedef enum {
    ESP_RST_UNKNOWN = 0, ESP_RST_POWERON = 1, ESP_RST_EXT = 2, ESP_RST_SW = 3,
    ESP_RST_PANIC = 4, ESP_RST_INT_WDT = 5, ESP_RST_TASK_WDT = 6, ESP_RST_WDT = 7,
    ESP_RST_DEEPSLEEP = 8, ESP_RST_BROWNOUT = 9, ESP_RST_SDIO = 10
} esp_reset_reason_t;
esp_reset_reason_t esp_reset_reason();
const esp_partition_t *esp_ota_get_running_partition();
const esp_partition_t *esp_ota_get_next_update_partition(const esp_partition_t *);
int esp_ota_set_boot_partition(const esp_partition_t *);
int esp_ota_mark_app_valid_cancel_rollback();
void ESP_restart_stub();
#define ESP_RESTART_DUMMY 0

// ---------------- ArduinoJson 6 ----------------
struct JsonVariant {
    template <typename T> T as() const { return T(); }
    template <typename T> T operator|(T def) const { return def; }
    template <typename T> JsonVariant &operator=(const T &) { return *this; }
    JsonVariant operator[](const char *) const { return JsonVariant(); }
    JsonVariant operator[](int) const { return JsonVariant(); }
    bool isNull() const { return false; }
};

struct JsonArray : JsonVariant {
    JsonArray() {}
    JsonArray(const JsonVariant &) {}
    size_t size() const { return 0; }
    JsonVariant operator[](int) const { return JsonVariant(); }
};

struct JsonDocument {
    void clear() {}
    JsonVariant operator[](const char *) { return JsonVariant(); }
};

template <size_t N>
struct StaticJsonDocument : JsonDocument {};

struct DynamicJsonDocument : JsonDocument {
    explicit DynamicJsonDocument(size_t) {}
};

struct DeserializationError {
    operator bool() const { return false; }
    const char *c_str() const { return ""; }
};

DeserializationError deserializeJson(JsonDocument &, const char *, size_t);
void serializeJson(JsonDocument &, String &);

// ---------------- BLE HID ----------------
typedef uint8_t MediaKeyReport[2];
typedef uint8_t KeyReport[8];

#define KEY_RETURN 0xB0
#define KEY_ESC    0xB1
#define KEY_TAB    0xB2

class BLECharacteristic {
public:
    uint8_t *getData() { return nullptr; }
    size_t getLength() { return 0; }
};

class BLECharacteristicCallbacks {
public:
    virtual ~BLECharacteristicCallbacks() {}
    virtual void onWrite(BLECharacteristic *) {}
};

class BLEServer {
public:
    uint16_t getConnId() { return 0; }
    bool requestConnParams(uint16_t, uint16_t, uint16_t, uint16_t, uint16_t) { return true; }
};

class BLEDevice {
public:
    static BLEServer *getServer() { return nullptr; }
    static bool startAdvertising() { return true; }
};

struct ble_gap_conn_desc { uint16_t conn_itvl; };
int ble_gap_conn_find(uint16_t, ble_gap_conn_desc *);

class BleComboKeyboard {
public:
    const char *deviceName;
    const char *deviceManufacturer;
    size_t press(uint8_t) { return 1; }
    size_t press(const MediaKeyReport) { return 1; }
    size_t release(uint8_t) { return 1; }
    size_t release(const MediaKeyReport) { return 1; }
    size_t write(uint8_t) { return 1; }
    size_t write(const MediaKeyReport) { return 1; }
    void releaseAll() {}
    bool isConnected() { return true; }
    void setCallbacks(BLECharacteristicCallbacks *) {}
    void begin() {}
};

class BleComboMouse {
public:
    void begin() {}
    void send(int, int16_t, int16_t, uint8_t = 1) {}
};

void hidInitDiag();
