# AutoJS HID 固件（ESP32-C3 / S3）

本目录存放与本 App「HID 触摸」功能配套的 ESP32 固件。

| 目录 / 文件 | 说明 |
| --- | --- |
| `esp32c3-hid/` | **本工程维护的新固件**（源码 + 编译产物说明），支持 OTA 在线升级 |
| `AutoJS-HID-ESP32-C3-S3-20260720.zip` | 原厂固件包（2026-07-20 版，仅支持 `0x06` 手势，无 OTA） |

---

## 为什么需要刷一次线

原厂固件只有一个 HTTP 路由 `POST /wifi`（配网），**没有 `/update`**，也没有 ArduinoOTA，
更没有 `0x07/0x08/0x09` 指令字。ESP32-C3 的 ROM 下载模式只认 UART / USB，
蓝牙和 WiFi 都进不去。所以：

- **第一次**必须物理连一次线（一次就够，手机 OTG 也行）
- **之后**所有升级在 App 里点「设置 → ESP32 HID 设备 → 固件升级」完成

好在原厂固件的分区表本来就有 `otadata + app0 + app1` 双分区，且与本工程**逐字节一致**
（已解析原厂 bin 的 `0x8000` 确认，otadata 内容也与本工程的 `boot_app0.bin` 完全相同）。

**因此首次刷机不需要整片刷，只写 app 分区到 `0x10000` 即可** ——
不动引导、不动分区表、不动 `nvs`，WiFi 配置完整保留，刷完不用重新配网。

## 首次刷机（USB，只需一次）

```bash
pip install esptool

# 只写 app，保留 WiFi 配置（推荐）
esptool --chip esp32c3 -p /dev/ttyUSB0 -b 460800 write_flash 0x10000 firmware.bin
```

Windows 把端口换成 `COM3` 之类。或打开 <https://espressif.github.io/esptool-js/> 用浏览器刷，
**地址填 `0x10000`**，选 `firmware.bin`。

原厂说明书写的是"0x0 + 整片镜像"，那是全清重刷，会擦掉 `nvs` 里的 WiFi 密码，
刷完得重新配网。已经跑原厂固件的板子没必要这么做。

`firmware-merged.bin` 只在板子是空的、或分区表被改坏需要救砖时用。

刷完蓝牙若连不上，取消配对重新配一次；WiFi 不用动。

## 之后的升级（App 内 OTA）

App → 设置 → ESP32 HID 设备 → 固件升级 → 选 `firmware.bin` → 开始升级。

- 写的是另一个 app 分区，写完才切启动项，**中途断电不变砖**
- 新固件起不来会自动回滚
- 升级期间 UDP 手势通道不停，HID 点击仍可用
- 完成后 App 会自动等设备重启并回读版本号，确认真的换上了

> `firmware-merged.bin` **只能用于 USB 刷机**。它有 1.32MB，带引导程序，
> 走 OTA 会写进 app 分区导致无法启动——App 里也会提示这种文件不能用于 OTA。


## 控制协议

### UDP（端口 21115）

载荷：1 字节指令 + JSON（极简键名，与原厂 App 抓包对齐）。

| 指令 | JSON | 说明 |
| --- | --- | --- |
| `0x06` | `{"a":[x1,y1,x2,y2],"b":300,"c":1200,"d":2670}` | 手势 / 点击 |
| `0x07` | `{"k":"BACK","n":1,"i":60}` | 按键（新） |
| `0x08` | `{"t":"hi","i":30}` | 键盘打字，仅 ASCII（新） |
| `0x09` | `{}` | 抬起复位（新） |

- `a`：扁平坐标数组，按下与抬起同一点即为单击，最多 64 个点
- `b`：手势时长 ms（0~60000）
- `c` / `d`：手机屏幕宽 / 高，固件据此换算成 digitizer 绝对坐标 `0~10000`

### HTTP（端口 80）

| 路径 | 说明 |
| --- | --- |
| `GET /` | 状态页 + 配网表单 + 升级表单 |
| `GET /ping` | 返回 `pong 1.1.0`，App 用它判在线与版本 |
| `GET /info` | JSON 详情 |
| `POST /wifi` | 配网 |
| `POST /update` | OTA 上传（`multipart`，字段 `firmware`，需 `.bin`） |

## 编译

详见 [`esp32c3-hid/README.md`](esp32c3-hid/README.md)。依赖已内置于 `esp32c3-hid/lib/`，
`arduino-cli compile` 一条命令即可；也可 push 后从 GitHub Actions 的
`esp32c3-hid-firmware` 产物直接下载 bin。

## 使用前准备

1. 在手机蓝牙里配对 `AutoJS HID-XXXX`（刷机后需重新配对一次）
2. 设备首次使用连其热点（密码 `autojs1234`）完成配网，之后接入与手机相同的 WiFi
3. App **设置 → ESP32 HID 设备** 里填设备 IP（可用「自动发现」），端口 `21115`
4. 点击类动作（HID 中继 / 点击图片 / 点击坐标 / 手势 / 点击颜色 / 点击控件）的执行通道选「HID 触摸(ESP32)」

## 已知注意事项

- 手机需与 ESP32 在同一局域网；若开了「智能多网络 / 双 WLAN 加速」，UDP 包可能走蜂窝口导致不通
- 蓝牙显示「已连接」不代表可用，刷机后务必重新配对
- 刷回原厂固件后，App 里的「HID 按键」「HID 键盘输入」会无响应（原厂固件不认识 `0x07/0x08`），
  点击类动作不受影响
