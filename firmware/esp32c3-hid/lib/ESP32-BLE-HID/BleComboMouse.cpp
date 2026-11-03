#include "BleComboMouse.h"

// 主程序提供的发送前体检（记录指针链 + 拦截坏指针）
extern bool hidSafeNotify(BLECharacteristic *c, const char *tag, const uint8_t *data, size_t len);


#define LSB(v) ((v >> 8) & 0xff)
#define MSB(v) (v & 0xff)
void BleComboMouse::click(uint8_t b)
{
  _buttons = b;
  move(0,0,0,0);
  _buttons = 0;
  move(0,0,0,0);
}

void BleComboMouse::move(signed char x, signed char y, signed char wheel, signed char hWheel)
{
  if (_keyboard->isConnected())
  {
    uint8_t m[5];
    m[0] = _buttons;
    m[1] = x;
    m[2] = y;
    m[3] = wheel;
    m[4] = hWheel;
    _keyboard->inputRELMouse->setValue(m, 5);
    hidSafeNotify(_keyboard->inputRELMouse, "relmove", m, 5);
  }
}

void BleComboMouse::send(int state, int16_t x, int16_t y, uint8_t contact)
{
  if (_keyboard->isConnected())
  {
    // 6 字节：state | ContactIdentifier | X16(LE) | Y16(LE)，与旧版官方固件一致
    uint8_t m[6];
    m[0] = state;
    m[1] = contact ? contact : 1; // Contact Identifier：0 视为 1
    m[2] = MSB(x);         // 宏名沿用历史，实际是低字节（小端）
    m[3] = LSB(x);
    m[4] = MSB(y);
    m[5] = LSB(y);
    _keyboard->inputMouse->setValue(m, 6);
    hidSafeNotify(_keyboard->inputMouse, "mousesend", m, 6);
  }
}


void BleComboMouse::buttons(uint8_t b)
{
  if (b != _buttons)
  {
    _buttons = b;
    move(0,0,0,0);
  }
}

void BleComboMouse::press(uint8_t b)
{
  buttons(_buttons | b);
}

void BleComboMouse::release(uint8_t b)
{
  buttons(_buttons & ~b);
}

bool BleComboMouse::isPressed(uint8_t b)
{
  if ((b & _buttons) > 0)
    return true;
  return false;
}
