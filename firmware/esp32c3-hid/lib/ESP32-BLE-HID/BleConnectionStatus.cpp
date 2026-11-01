#include "BleConnectionStatus.h"
#include <Arduino.h>

// 主程序里定义的事件记录器（用于 /blelog 页面排查配对问题）
extern void bleEvent(const char *msg);

BleConnectionStatus::BleConnectionStatus(void) {
}

void BleConnectionStatus::onConnect(BLEServer* pServer)
{
  this->connected = true;
  Serial.println("[BLE] onConnect");
  bleEvent("onConnect");
  {
    extern void hidDiag(const char *tag, BLECharacteristic *c);
    hidDiag("onConnect kb", this->inputKeyboard);
    hidDiag("onConnect mouse", this->inputMouse);
  }

  // 描述符可能还没建好（回连时存在时序差异），判空避免空指针崩溃
  if (this->inputKeyboard != nullptr) {
    BLE2902* desc = (BLE2902*)this->inputKeyboard->getDescriptorByUUID(BLEUUID((uint16_t)0x2902));
    if (desc != nullptr) desc->setNotifications(true);
  }
  if (this->inputMouse != nullptr) {
    BLE2902* desc = (BLE2902*)this->inputMouse->getDescriptorByUUID(BLEUUID((uint16_t)0x2902));
    if (desc != nullptr) desc->setNotifications(true);
  }
}

void BleConnectionStatus::onDisconnect(BLEServer* pServer)
{
  this->connected = false;
  Serial.println("[BLE] onDisconnect");
  bleEvent("onDisconnect");

  if (this->inputKeyboard != nullptr) {
    BLE2902* desc = (BLE2902*)this->inputKeyboard->getDescriptorByUUID(BLEUUID((uint16_t)0x2902));
    if (desc != nullptr) desc->setNotifications(false);
  }
  if (this->inputMouse != nullptr) {
    BLE2902* desc = (BLE2902*)this->inputMouse->getDescriptorByUUID(BLEUUID((uint16_t)0x2902));
    if (desc != nullptr) desc->setNotifications(false);
  }

  // 关键：原库断开后不重新开广播，手机配对失败一次就再也搜不到，
  // 必须自己补上，否则表现为"配对转几秒设备消失"。
  if (pServer != nullptr) {
    pServer->startAdvertising();
    Serial.println("[BLE] advertising restarted");
    bleEvent("adv restart");
  }
}
