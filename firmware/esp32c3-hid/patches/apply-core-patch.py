#!/usr/bin/env python3
"""构建前给 arduino-esp32 核心打补丁。

新版 arduino-esp32（3.x）在 ESP32-C3 上默认启用 NimBLE 协议栈，其
BLEService::addCharacteristic() 按 UUID 去重特征：BLEHIDDevice 的所有
输入/输出报告特征共用 UUID 0x2A4D，只有第一个能注册，其余（鼠标、
相对鼠标、媒体键、键盘输出）被静默丢弃，getHandle() 恒为 0xFFFF。

HID over GATT 规范要求多个 Report 特征共用 0x2A4D（靠 Report Reference
描述符区分），Android 等主机也只认 0x2A4D。本补丁去掉该去重，
让重复 UUID 的特征全部注册（核心库的 map 按对象指针存储，本就支持）。

幂等：已打过补丁的文件直接跳过。
"""
import glob, sys

OLD = '''#if defined(CONFIG_NIMBLE_ENABLED)
  if (pExisting != nullptr) {
    pExisting->m_removed = 0;
  } else
#endif
  {
    // Remember this characteristic in our map of characteristics.  At this point, we can lookup by UUID
    // but not by handle.  The handle is allocated to us on the ESP_GATTS_ADD_CHAR_EVT.
    m_characteristicMap.setByUUID(pCharacteristic, pCharacteristic->getUUID());
  }'''
NEW = '''#if defined(CONFIG_NIMBLE_ENABLED)
  if (pExisting != nullptr) {
    pExisting->m_removed = 0;
  }
#endif
  {
    // Remember this characteristic in our map of characteristics.  At this point, we can lookup by UUID
    // but not by handle.  The handle is allocated to us on the ESP_GATTS_ADD_CHAR_EVT.
    // PATCH(TT): register duplicate-UUID characteristics too - HID over GATT needs
    // several 0x2A4D report characteristics; upstream drops them silently.
    m_characteristicMap.setByUUID(pCharacteristic, pCharacteristic->getUUID());
  }'''

patched = 0
for f in glob.glob(sys.argv[1] if len(sys.argv) > 1 else
                   '/root/.arduino15/packages/esp32/hardware/esp32/*/libraries/BLE/src/BLEService.cpp'):
    s = open(f).read()
    if NEW in s:
        print('[patch] already applied:', f); continue
    if OLD not in s:
        print('[patch] WARNING pattern not found:', f); continue
    open(f, 'w').write(s.replace(OLD, NEW))
    print('[patch] applied:', f); patched += 1
sys.exit(0)
