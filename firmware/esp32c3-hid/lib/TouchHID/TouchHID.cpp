// TouchHID —— NimBLE-Arduino 2.x 原生实现的 BLE HID 组合设备
// （键盘 + 绝对触摸屏 + 消费键）。取代旧 Bluedroid 时代的 ESP32-BLE-HID fork：
// 旧 fork 在 arduino-esp32 3.x 的 NimBLE 模式下有多个致命问题
// （同 UUID 报告特征被去重、notify 指针链崩溃、String 截断 0x00），
// 这也是 v1.1.4~v1.2.0 一系列修复的根源；NimBLE-Arduino 原生支持
// 多个 0x2A4D 报告特征，无需任何核心补丁。
//
// 对外接口与旧 fork 完全一致：Keyboard / Mouse 全局实例、
// press/release/write/releaseAll、Mouse.send(state, x, y)。

#include "TouchHID.h"
#include <Arduino.h>

// Report IDs（与报告描述符一一对应）
#define KEYBOARD_ID   0x01
#define MEDIA_KEYS_ID 0x02
#define MOUSE_ID      0x03
#define REL_MOUSE_ID  0x04

// 主程序提供的事件轨迹与统计（/blelog 可见）
extern void traceAppend(const char *line);
extern void bleEvent(const char *msg);
extern uint32_t hidSendCount, hidSkipCount, lastHidSendAt;

static void hidLog(const char *tag, NimBLECharacteristic *c, size_t len) {
  char line[128];
  snprintf(line, sizeof(line), "[%lus] HID %s hnd=%u len=%u\n",
           (unsigned long) (millis() / 1000), tag,
           (unsigned) ((c != nullptr) ? c->getHandle() : 0), (unsigned) len);
  traceAppend(line);
  Serial.print("[HID-TRACE] ");
  Serial.print(line);
}

static const uint8_t _hidReportDescriptor[] = {
  USAGE_PAGE(1),      0x01,          // USAGE_PAGE (Generic Desktop Ctrls)
  USAGE(1),           0x06,          // USAGE (Keyboard)
  COLLECTION(1),      0x01,          // COLLECTION (Application)
  // ------------------------------------------------- Keyboard
  REPORT_ID(1),       KEYBOARD_ID,   //   REPORT_ID (1)
  USAGE_PAGE(1),      0x07,          //   USAGE_PAGE (Kbrd/Keypad)
  USAGE_MINIMUM(1),   0xE0,          //   USAGE_MINIMUM (0xE0)
  USAGE_MAXIMUM(1),   0xE7,          //   USAGE_MAXIMUM (0xE7)
  LOGICAL_MINIMUM(1), 0x00,          //   LOGICAL_MINIMUM (0)
  LOGICAL_MAXIMUM(1), 0x01,          //   Logical Maximum (1)
  REPORT_SIZE(1),     0x01,          //   REPORT_SIZE (1)
  REPORT_COUNT(1),    0x08,          //   REPORT_COUNT (8)
  HIDINPUT(1),        0x02,          //   INPUT (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
  REPORT_COUNT(1),    0x01,          //   REPORT_COUNT (1) ; 1 byte (Reserved)
  REPORT_SIZE(1),     0x08,          //   REPORT_SIZE (8)
  HIDINPUT(1),        0x01,          //   INPUT (Const,Array,Abs,No Wrap,Linear,Preferred State,No Null Position)
  REPORT_COUNT(1),    0x05,          //   REPORT_COUNT (5) ; 5 bits (Num lock, Caps lock, Scroll lock, Compose, Kana)
  REPORT_SIZE(1),     0x01,          //   REPORT_SIZE (1)
  USAGE_PAGE(1),      0x08,          //   USAGE_PAGE (LEDs)
  USAGE_MINIMUM(1),   0x01,          //   USAGE_MINIMUM (0x01) ; Num Lock
  USAGE_MAXIMUM(1),   0x05,          //   USAGE_MAXIMUM (0x05) ; Kana
  HIDOUTPUT(1),       0x02,          //   OUTPUT (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile)
  REPORT_COUNT(1),    0x01,          //   REPORT_COUNT (1) ; 3 bits (Padding)
  REPORT_SIZE(1),     0x03,          //   REPORT_SIZE (3)
  HIDOUTPUT(1),       0x01,          //   OUTPUT (Const,Array,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile)
  REPORT_COUNT(1),    0x06,          //   REPORT_COUNT (6) ; 6 bytes (Keys)
  REPORT_SIZE(1),     0x08,          //   REPORT_SIZE(8)
  LOGICAL_MINIMUM(1), 0x00,          //   LOGICAL_MINIMUM(0)
  LOGICAL_MAXIMUM(1), 0x65,          //   LOGICAL_MAXIMUM(0x65) ; 101 keys
  USAGE_PAGE(1),      0x07,          //   USAGE_PAGE (Kbrd/Keypad)
  USAGE_MINIMUM(1),   0x00,          //   USAGE_MINIMUM (0)
  USAGE_MAXIMUM(1),   0x65,          //   USAGE_MAXIMUM (0x65)
  HIDINPUT(1),        0x00,          //   INPUT (Data,Array,Abs,No Wrap,Linear,Preferred State,No Null Position)
  END_COLLECTION(0),                 // END_COLLECTION
  // ------------------------------------------------- Media Keys
  USAGE_PAGE(1),      0x0C,          // USAGE_PAGE (Consumer)
  USAGE(1),           0x01,          // USAGE (Consumer Control)
  COLLECTION(1),      0x01,          // COLLECTION (Application)
  REPORT_ID(1),       MEDIA_KEYS_ID, //   REPORT_ID (3)
  USAGE_PAGE(1),      0x0C,          //   USAGE_PAGE (Consumer)
  LOGICAL_MINIMUM(1), 0x00,          //   LOGICAL_MINIMUM (0)
  LOGICAL_MAXIMUM(1), 0x01,          //   LOGICAL_MAXIMUM (1)
  REPORT_SIZE(1),     0x01,          //   REPORT_SIZE (1)
  REPORT_COUNT(1),    0x10,          //   REPORT_COUNT (16)
  USAGE(1),           0xB5,          //   USAGE (Scan Next Track)     ; bit 0: 1
  USAGE(1),           0xB6,          //   USAGE (Scan Previous Track) ; bit 1: 2
  USAGE(1),           0xB7,          //   USAGE (Stop)                ; bit 2: 4
  USAGE(1),           0xCD,          //   USAGE (Play/Pause)          ; bit 3: 8
  USAGE(1),           0xE2,          //   USAGE (Mute)                ; bit 4: 16
  USAGE(1),           0xE9,          //   USAGE (Volume Increment)    ; bit 5: 32
  USAGE(1),           0xEA,          //   USAGE (Volume Decrement)    ; bit 6: 64
  USAGE(2),           0x23, 0x02,    //   Usage (WWW Home)            ; bit 7: 128
  USAGE(2),           0x94, 0x01,    //   Usage (My Computer) ; bit 0: 1
  USAGE(2),           0x92, 0x01,    //   Usage (Calculator)  ; bit 1: 2
  USAGE(2),           0x2A, 0x02,    //   Usage (WWW fav)     ; bit 2: 4
  USAGE(2),           0x21, 0x02,    //   Usage (WWW search)  ; bit 3: 8
  USAGE(2),           0x26, 0x02,    //   Usage (WWW stop)    ; bit 4: 16
  USAGE(2),           0x24, 0x02,    //   Usage (WWW back)    ; bit 5: 32
  USAGE(2),           0x83, 0x01,    //   Usage (Media sel)   ; bit 6: 64
  USAGE(2),           0x8A, 0x01,    //   Usage (Mail)        ; bit 7: 128
  HIDINPUT(1),        0x02,          //   INPUT (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
  END_COLLECTION(0),                 // END_COLLECTION

  // ------------------------------------------------- ABS Mouse
  0x05, 0x0d,                    /* USAGE_PAGE (Digitizer) */
  0x09, 0x04,                    /* USAGE (Touch Screen) */
  0xa1, 0x01,                    /* COLLECTION (Application) */
  0x85, MOUSE_ID,          /*    REPORT_ID */

  /* 以下结构逐字节复刻旧版官方固件（从其固件镜像提取的报告描述符）：
   * Usage(Finger) + Collection(Logical)，含 Contact Identifier，
   * X/Y 为 16-bit、Logical Maximum 32767。Android 只对这种结构走
   * 标准触摸屏线性映射（工具=手指、坐标精确）；Self 风格的
   * Pointer/Stylus 物理集合会被当成绝对指针做等比裁切映射。 */
  0x09, 0x22,                    /*   Usage (Finger) */
  0xA1, 0x02,                    /*   Collection (Logical) */

  0x09, 0x42,                    /*     Usage (Tip Switch) */
  0x09, 0x32,                    /*     Usage (In Range) */
  0x15, 0x00,                    /*     Logical Min (0) */
  0x25, 0x01,                    /*     Logical Max (1) */
  0x75, 0x01,                    /*     Report Size (1) */
  0x95, 0x02,                    /*     Report Count (2) */
  0x81, 0x02,                    /*     Input (Data,Var,Abs) */

  0x75, 0x01,                    /*     Report Size (1) */
  0x95, 0x06,                    /*     Report Count (6) */
  0x81, 0x01,                    /*     Input (Const,Ary,Abs) */

  0x09, 0x51,                    /*     Usage (Contact Identifier) */
  0x75, 0x08,                    /*     Report Size (8) */
  0x95, 0x01,                    /*     Report Count (1) */
  0x15, 0x00,                    /*     Logical Min (0) */
  0x25, 0x7f,                    /*     Logical Max (127) */
  0x81, 0x02,                    /*     Input (Data,Var,Abs) */

  0x05, 0x01,                    /*     Usage Page (Generic Desktop) */
  0x09, 0x30,                    /*     Usage (X) */
  0x09, 0x31,                    /*     Usage (Y) */
  0x16, 0x00, 0x00,              /*     Logical Minimum (0) */
  0x26, 0xff, 0x7f,              /*     Logical Maximum (32767) */
  0x75, 0x10,                    /*     Report Size (16) */
  0x95, 0x02,                    /*     Report Count (2) */
  0x81, 0x02,                    /*     Input (Data,Var,Abs) */
  0xc0,                          /*   END_COLLECTION (Logical) */
  0xc0,                           /* END_COLLECTION (Application) */

  // With this declaration a data packet must be sent as:
  // byte 1   -> "touch" state          (bit 0 = pen up/down, bit 1 = In Range)
  // byte 2,3 -> absolute X coordinate  (0...10000)
  // byte 4,5 -> absolute Y coordinate  (0...10000)
//----------------REL Mouse
 USAGE_PAGE(1),       0x01, // USAGE_PAGE (Generic Desktop)
 USAGE(1),            0x02, // USAGE (Mouse)
 COLLECTION(1),       0x01, // COLLECTION (Application)
 USAGE(1),            0x01, //   USAGE (Pointer)
 COLLECTION(1),       0x00, //   COLLECTION (Physical)
 REPORT_ID(1),        REL_MOUSE_ID, //     REPORT_ID (4)
 // ------------------------------------------------- Buttons (Left, Right, Middle, Back, Forward)
 USAGE_PAGE(1),       0x09, //     USAGE_PAGE (Button)
 USAGE_MINIMUM(1),    0x01, //     USAGE_MINIMUM (Button 1)
 USAGE_MAXIMUM(1),    0x05, //     USAGE_MAXIMUM (Button 5)
 LOGICAL_MINIMUM(1),  0x00, //     LOGICAL_MINIMUM (0)
 LOGICAL_MAXIMUM(1),  0x01, //     LOGICAL_MAXIMUM (1)
 REPORT_SIZE(1),      0x01, //     REPORT_SIZE (1)
 REPORT_COUNT(1),     0x05, //     REPORT_COUNT (5)
 HIDINPUT(1),         0x02, //     INPUT (Data, Variable, Absolute) ;5 button bits
 // ------------------------------------------------- Padding
 REPORT_SIZE(1),      0x03, //     REPORT_SIZE (3)
 REPORT_COUNT(1),     0x01, //     REPORT_COUNT (1)
 HIDINPUT(1),         0x03, //     INPUT (Constant, Variable, Absolute) ;3 bit padding
 // ------------------------------------------------- X/Y position, Wheel
 USAGE_PAGE(1),       0x01, //     USAGE_PAGE (Generic Desktop)
 USAGE(1),            0x30, //     USAGE (X)
 USAGE(1),            0x31, //     USAGE (Y)
 USAGE(1),            0x38, //     USAGE (Wheel)
 LOGICAL_MINIMUM(1),  0x81, //     LOGICAL_MINIMUM (-127)
 LOGICAL_MAXIMUM(1),  0x7f, //     LOGICAL_MAXIMUM (127)
 REPORT_SIZE(1),      0x08, //     REPORT_SIZE (8)
 REPORT_COUNT(1),     0x03, //     REPORT_COUNT (3)
 HIDINPUT(1),         0x06, //     INPUT (Data, Variable, Relative) ;3 bytes (X,Y,Wheel)
 // ------------------------------------------------- Horizontal wheel
 USAGE_PAGE(1),       0x0c, //     USAGE PAGE (Consumer Devices)
 USAGE(2),      0x38, 0x02, //     USAGE (AC Pan)
 LOGICAL_MINIMUM(1),  0x81, //     LOGICAL_MINIMUM (-127)
 LOGICAL_MAXIMUM(1),  0x7f, //     LOGICAL_MAXIMUM (127)
 REPORT_SIZE(1),      0x08, //     REPORT_SIZE (8)
 REPORT_COUNT(1),     0x01, //     REPORT_COUNT (1)
 HIDINPUT(1),         0x06, //     INPUT (Data, Var, Rel)
 END_COLLECTION(0),         //   END_COLLECTION
 END_COLLECTION(0)          // END_COLLECTION

};

BleComboKeyboard::BleComboKeyboard(std::string deviceName,
                                   std::string deviceManufacturer,
                                   uint8_t batteryLevel)
    : batteryLevel(batteryLevel),
      deviceManufacturer(deviceManufacturer),
      deviceName(deviceName) {}

void BleComboKeyboard::begin(void) {
  // 与旧 fork 相同的异步模式：begin() 只创建任务，BLEDevice::init() 在
  // 任务线程里执行；调用方在 begin() 之后不能立刻操作 BLE API。
  xTaskCreate(BleComboKeyboard::taskServer, "server", 8192, this, 5, NULL);
}

bool BleComboKeyboard::isConnected(void) {
  return this->connected;
}

void BleComboKeyboard::setBatteryLevel(uint8_t level) {
  this->batteryLevel = level;
  if (this->hid != nullptr)
    this->hid->setBatteryLevel(this->batteryLevel);
}

void BleComboKeyboard::onConnect(NimBLEServer* pServer, NimBLEConnInfo& connInfo) {
  this->connected = true;
  Serial.println("[BLE] onConnect");
  bleEvent("onConnect");
}

void BleComboKeyboard::onDisconnect(NimBLEServer* pServer, NimBLEConnInfo& connInfo, int reason) {
  this->connected = false;
  Serial.printf("[BLE] onDisconnect reason=%d\n", reason);
  bleEvent("onDisconnect");
  // NimBLE 不会自动重开广播：断开后立刻重开，否则设备永久静音
  NimBLEDevice::getAdvertising()->start();
  Serial.println("[BLE] advertising restarted");
  bleEvent("adv restart");
}

void BleComboKeyboard::taskServer(void* pvParameter) {
  BleComboKeyboard* k = (BleComboKeyboard*) pvParameter;
  NimBLEDevice::init(k->deviceName);
  NimBLEServer* server = NimBLEDevice::createServer();
  server->setCallbacks(k);

  k->hid = new NimBLEHIDDevice(server);
  k->inputKeyboard  = k->hid->getInputReport(KEYBOARD_ID);
  k->outputKeyboard = k->hid->getOutputReport(KEYBOARD_ID);
  k->inputMediaKeys = k->hid->getInputReport(MEDIA_KEYS_ID);
  // NimBLE-Arduino 原生支持同 UUID(0x2A4D) 的多个报告特征，
  // 靠 Report Reference 描述符里的 reportId 区分（规范行为）。
  k->inputMouse     = k->hid->getInputReport(MOUSE_ID);
  k->inputRELMouse  = k->hid->getInputReport(REL_MOUSE_ID);

  k->hid->setManufacturer(k->deviceManufacturer);
  k->hid->setPnp(0x02, 0xe502, 0xa111, 0x0210);
  k->hid->setHidInfo(0x00, 0x01);

  // 安全参数：LE Secure Connections + 绑定，IO 能力无屏无键盘（Just Works）。
  // 双向分发加密密钥与身份密钥：Android 回连常用 RPA，没有 ID 密钥无法认回自己。
  NimBLEDevice::setSecurityAuth(true /*bonding*/, false /*mitm*/, true /*sc*/);
  NimBLEDevice::setSecurityInitKey(BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID);
  NimBLEDevice::setSecurityRespKey(BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID);

  k->hid->setReportMap((uint8_t*) _hidReportDescriptor, sizeof(_hidReportDescriptor));

  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  adv->setAppearance(0x03C1);  // HID Keyboard（外设图标）
  adv->addServiceUUID(k->hid->getHidService()->getUUID());
  adv->enableScanResponse(true);
  adv->start();

  k->hid->setBatteryLevel(k->batteryLevel);

  {
    char line[128];
    snprintf(line, sizeof(line), "[%lus] DIAG svc-started kb=%p(%u) mouse=%p(%u) rel=%p(%u)\n",
             (unsigned long) (millis() / 1000),
             (void*) k->inputKeyboard,  (unsigned) k->inputKeyboard->getHandle(),
             (void*) k->inputMouse,     (unsigned) k->inputMouse->getHandle(),
             (void*) k->inputRELMouse,  (unsigned) k->inputRELMouse->getHandle());
    traceAppend(line);
    Serial.print("[HID-TRACE] ");
    Serial.print(line);
  }

  Serial.println("[BLE] HID services started, advertising");
  vTaskDelay(portMAX_DELAY);
}

// ---------------- 键盘报告发送 ----------------
void BleComboKeyboard::sendReport(KeyReport* keys) {
  if (!this->isConnected() || this->inputKeyboard == nullptr) {
    hidSkipCount++;
    return;
  }
  this->inputKeyboard->setValue((uint8_t*) keys, sizeof(KeyReport));
  this->inputKeyboard->notify();
  hidSendCount++;
  lastHidSendAt = millis();
}

void BleComboKeyboard::sendReport(MediaKeyReport* keys) {
  if (!this->isConnected() || this->inputMediaKeys == nullptr) {
    hidSkipCount++;
    return;
  }
  this->inputMediaKeys->setValue((uint8_t*) keys, sizeof(MediaKeyReport));
  this->inputMediaKeys->notify();
  hidSendCount++;
  lastHidSendAt = millis();
}

#define SHIFT 0x80
const uint8_t _asciimap[128] =
{
	0x00,             // NUL
	0x00,             // SOH
	0x00,             // STX
	0x00,             // ETX
	0x00,             // EOT
	0x00,             // ENQ
	0x00,             // ACK
	0x00,             // BEL
	0x2a,			// BS	Backspace
	0x2b,			// TAB	Tab
	0x28,			// LF	Enter
	0x00,             // VT
	0x00,             // FF
	0x00,             // CR
	0x00,             // SO
	0x00,             // SI
	0x00,             // DEL
	0x00,             // DC1
	0x00,             // DC2
	0x00,             // DC3
	0x00,             // DC4
	0x00,             // NAK
	0x00,             // SYN
	0x00,             // ETB
	0x00,             // CAN
	0x00,             // EM
	0x00,             // SUB
	0x00,             // ESC
	0x00,             // FS
	0x00,             // GS
	0x00,             // RS
	0x00,             // US

	0x2c,		   //  ' '
	0x1e|SHIFT,	   // !
	0x34|SHIFT,	   // "
	0x20|SHIFT,    // #
	0x21|SHIFT,    // $
	0x22|SHIFT,    // %
	0x24|SHIFT,    // &
	0x34,          // '
	0x26|SHIFT,    // (
	0x27|SHIFT,    // )
	0x25|SHIFT,    // *
	0x2e|SHIFT,    // +
	0x36,          // ,
	0x2d,          // -
	0x37,          // .
	0x38,          // /
	0x27,          // 0
	0x1e,          // 1
	0x1f,          // 2
	0x20,          // 3
	0x21,          // 4
	0x22,          // 5
	0x23,          // 6
	0x24,          // 7
	0x25,          // 8
	0x26,          // 9
	0x33|SHIFT,      // :
	0x33,          // ;
	0x36|SHIFT,      // <
	0x2e,          // =
	0x37|SHIFT,      // >
	0x38|SHIFT,      // ?
	0x1f|SHIFT,      // @
	0x04|SHIFT,      // A
	0x05|SHIFT,      // B
	0x06|SHIFT,      // C
	0x07|SHIFT,      // D
	0x08|SHIFT,      // E
	0x09|SHIFT,      // F
	0x0a|SHIFT,      // G
	0x0b|SHIFT,      // H
	0x0c|SHIFT,      // I
	0x0d|SHIFT,      // J
	0x0e|SHIFT,      // K
	0x0f|SHIFT,      // L
	0x10|SHIFT,      // M
	0x11|SHIFT,      // N
	0x12|SHIFT,      // O
	0x13|SHIFT,      // P
	0x14|SHIFT,      // Q
	0x15|SHIFT,      // R
	0x16|SHIFT,      // S
	0x17|SHIFT,      // T
	0x18|SHIFT,      // U
	0x19|SHIFT,      // V
	0x1a|SHIFT,      // W
	0x1b|SHIFT,      // X
	0x1c|SHIFT,      // Y
	0x1d|SHIFT,      // Z
	0x2f,          // [
	0x31,          // bslash
	0x30,          // ]
	0x23|SHIFT,    // ^
	0x2d|SHIFT,    // _
	0x35,          // `
	0x04,          // a
	0x05,          // b
	0x06,          // c
	0x07,          // d
	0x08,          // e
	0x09,          // f
	0x0a,          // g
	0x0b,          // h
	0x0c,          // i
	0x0d,          // j
	0x0e,          // k
	0x0f,          // l
	0x10,          // m
	0x11,          // n
	0x12,          // o
	0x13,          // p
	0x14,          // q
	0x15,          // r
	0x16,          // s
	0x17,          // t
	0x18,          // u
	0x19,          // v
	0x1a,          // w
	0x1b,          // x
	0x1c,          // y
	0x1d,          // z
	0x2f|SHIFT,    // {
	0x31|SHIFT,    // |
	0x30|SHIFT,    // }
	0x35|SHIFT,    // ~
	0				// DEL
};

size_t BleComboKeyboard::press(uint8_t k) {
  uint8_t i;
  if (k >= 136) {
    k = k - 136;
  } else if (k >= 128) {
    _keyReport.modifiers |= (1 << (k - 128));
    k = 0;
  } else {
    k = pgm_read_byte(_asciimap + k);
    if (!k) {
      setWriteError();
      return 0;
    }
    if (k & 0x80) {
      _keyReport.modifiers |= 0x02;
      k &= 0x7F;
    }
  }
  if (_keyReport.keys[0] != k && _keyReport.keys[1] != k &&
      _keyReport.keys[2] != k && _keyReport.keys[3] != k &&
      _keyReport.keys[4] != k && _keyReport.keys[5] != k) {
    for (i = 0; i < 6; i++) {
      if (_keyReport.keys[i] == 0x00) {
        _keyReport.keys[i] = k;
        break;
      }
    }
    if (i == 6) {
      setWriteError();
      return 0;
    }
  }
  sendReport(&_keyReport);
  return 1;
}

size_t BleComboKeyboard::press(const MediaKeyReport k) {
  uint16_t k_16 = k[1] | (k[0] << 8);
  uint16_t mediaKeyReport_16 = _mediaKeyReport[1] | (_mediaKeyReport[0] << 8);
  mediaKeyReport_16 |= k_16;
  _mediaKeyReport[0] = (uint8_t) ((mediaKeyReport_16 & 0xFF00) >> 8);
  _mediaKeyReport[1] = (uint8_t) (mediaKeyReport_16 & 0x00FF);
  sendReport(&_mediaKeyReport);
  return 1;
}

size_t BleComboKeyboard::release(uint8_t k) {
  uint8_t i;
  if (k >= 136) {
    k = k - 136;
  } else if (k >= 128) {
    _keyReport.modifiers &= ~(1 << (k - 128));
    k = 0;
  } else {
    k = pgm_read_byte(_asciimap + k);
    if (!k) return 0;
    if (k & 0x80) {
      _keyReport.modifiers &= ~(0x02);
      k &= 0x7F;
    }
  }
  for (i = 0; i < 6; i++) {
    if (0 != k && _keyReport.keys[i] == k) {
      _keyReport.keys[i] = 0x00;
    }
  }
  sendReport(&_keyReport);
  return 1;
}

size_t BleComboKeyboard::release(const MediaKeyReport k) {
  uint16_t k_16 = k[1] | (k[0] << 8);
  uint16_t mediaKeyReport_16 = _mediaKeyReport[1] | (_mediaKeyReport[0] << 8);
  mediaKeyReport_16 &= ~k_16;
  _mediaKeyReport[0] = (uint8_t) ((mediaKeyReport_16 & 0xFF00) >> 8);
  _mediaKeyReport[1] = (uint8_t) (mediaKeyReport_16 & 0x00FF);
  sendReport(&_mediaKeyReport);
  return 1;
}

void BleComboKeyboard::releaseAll(void) {
  _keyReport.keys[0] = 0;
  _keyReport.keys[1] = 0;
  _keyReport.keys[2] = 0;
  _keyReport.keys[3] = 0;
  _keyReport.keys[4] = 0;
  _keyReport.keys[5] = 0;
  _keyReport.modifiers = 0;
  _mediaKeyReport[0] = 0;
  _mediaKeyReport[1] = 0;
  sendReport(&_keyReport);
}

size_t BleComboKeyboard::write(uint8_t c) {
  uint8_t p = press(c);
  release(c);
  return p;
}

size_t BleComboKeyboard::write(const MediaKeyReport c) {
  uint16_t p = press(c);
  release(c);
  return p;
}

size_t BleComboKeyboard::write(const uint8_t* buffer, size_t size) {
  size_t n = 0;
  while (size--) {
    if (*buffer != '\r') {
      if (write(*buffer)) n++;
      else break;
    }
    buffer++;
  }
  return n;
}

// ---------------- 鼠标 / 绝对触摸 ----------------
void BleComboMouse::move(signed char x, signed char y, signed char wheel, signed char hWheel) {
  if (!_keyboard->isConnected() || _keyboard->inputRELMouse == nullptr) {
    hidSkipCount++;
    return;
  }
  uint8_t m[5];
  m[0] = _buttons;
  m[1] = x;
  m[2] = y;
  m[3] = wheel;
  m[4] = hWheel;
  _keyboard->inputRELMouse->setValue(m, 5);
  _keyboard->inputRELMouse->notify();
  hidLog("relmove", _keyboard->inputRELMouse, 5);
  hidSendCount++;
  lastHidSendAt = millis();
}

void BleComboMouse::send(int state, int16_t x, int16_t y) {
  if (!_keyboard->isConnected() || _keyboard->inputMouse == nullptr) {
    hidSkipCount++;
    return;
  }
  // 6 字节：state | ContactIdentifier | X16(LE) | Y16(LE)，与旧版官方固件一致
  uint8_t m[6];
  m[0] = (uint8_t) state;
  m[1] = 1;              // Contact Identifier：单指固定 1
  m[2] = (uint8_t) (x & 0xff);        // X 低位（小端）
  m[3] = (uint8_t) ((x >> 8) & 0xff); // X 高位
  m[4] = (uint8_t) (y & 0xff);
  m[5] = (uint8_t) ((y >> 8) & 0xff);
  _keyboard->inputMouse->setValue(m, 6);
  _keyboard->inputMouse->notify();
  hidLog("mousesend", _keyboard->inputMouse, 6);
  hidSendCount++;
  lastHidSendAt = millis();
}

void BleComboMouse::click(uint8_t b) {
  _buttons = b;
  move(0, 0, 0, 0);
  _buttons = 0;
  move(0, 0, 0, 0);
}

void BleComboMouse::buttons(uint8_t b) {
  if (b != _buttons) {
    _buttons = b;
    move(0, 0, 0, 0);
  }
}

void BleComboMouse::press(uint8_t b) {
  buttons(_buttons | b);
}

void BleComboMouse::release(uint8_t b) {
  buttons(_buttons & ~b);
}

bool BleComboMouse::isPressed(uint8_t b) {
  return (b & _buttons) > 0;
}
