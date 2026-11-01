#ifndef ESP32_BLE_COMBO_H
#define ESP32_BLE_COMBO_H
#include "BleComboKeyboard.h"
#include "BleComboMouse.h"

extern BleComboMouse Mouse;
extern BleComboKeyboard Keyboard;

// 1.1.6 HID 安全发送：绕开 BLECharacteristic::notify() 的指针链
bool hidSafeNotify(BLECharacteristic *c, const char *tag, const uint8_t *data, size_t len);
void hidDiag(const char *tag, BLECharacteristic *c);
void hidInitDiag();

#endif
