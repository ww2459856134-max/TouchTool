# ESP32-C3 蓝牙 HID 固件（TouchTool 配套）

在原厂 AutoJS HID 固件基础上增加：**OTA 在线升级**、**按键注入**、**键盘打字**、**抬起复位**，
同时完整保留原 UDP 手势协议，老任务不需要改。

---

## 一、两种刷机方式

### 方式 A：首次 USB 刷机（只需一次）

原厂固件**没有** `/update` 接口，所以第一次必须插线。之后所有升级都能在手机 App 里点几下完成。

```bash
# 装一次 esptool 即可
pip install esptool

# 整片烧录，一条命令搞定
esptool --chip esp32c3 -p /dev/ttyUSB0 -b 460800 \
        write_flash 0x0 firmware-merged.bin
```

Windows 把 `/dev/ttyUSB0` 换成 `COM3` 之类。也可以直接用浏览器刷：
打开 <https://espressif.github.io/esptool-js/>，连上串口，地址填 `0x0`，选 `firmware-merged.bin`。

> **`firmware-merged.bin` 只能用于 USB 刷机，不要拿去 OTA。**
> 它带引导程序，OTA 会把它写进 app 分区，设备将无法启动。

### 方式 B：之后在 App 内 OTA（常用）

App → 设置 → ESP32 HID 设备 → **固件升级** → 选 `firmware.bin` → 开始升级。

升级写的是另一个 app 分区，写完才切换启动项，所以：

- 上传到一半断电，设备仍会启动旧固件（不会变砖）
- 新固件启动失败会自动回滚
- WiFi 配置、蓝牙配对全部保留
- 升级期间 UDP 手势通道不停，HID 点击仍可用

---

## 二、产物说明

| 文件 | 用途 |
| --- | --- |
| `firmware.bin` | **App 内 OTA 专用**，约 1.3MB，只含 app 分区 |
| `firmware-merged.bin` | 首次 USB 刷机，含 bootloader + 分区表 + app，整片写 `0x0` |
| `bootloader.bin` / `partitions.bin` / `boot_app0.bin` | 手动分地址刷写时用 |

分区表和原厂固件**逐字节一致**，所以首次刷机后不需要重新配网、不需要重新配对蓝牙。

---

## 三、编译

不用装 PlatformIO，用 Arduino CLI 即可（`lib/` 里已经内置了依赖，离线可编）。

```bash
# 1. 装核心（只需一次）
arduino-cli core install esp32:esp32

# 2. 编译
arduino-cli compile \
  --fqbn "esp32:esp32:esp32c3:PartitionScheme=custom,FlashSize=4M" \
  --build-property "build.custom_partitions=$PWD/partitions.csv" \
  --library "$PWD/lib/ESP32-BLE-HID" \
  --library "$PWD/lib/ArduinoJson" \
  --output-dir out \
  src

# 3. 合并整片镜像
esptool --chip esp32c3 merge-bin -o out/firmware-merged.bin \
  --flash-mode qio --flash-freq 80m --flash-size 4MB \
  0x0    out/<sketch>.ino.bootloader.bin \
  0x8000 out/<sketch>.ino.partitions.bin \
  0xe000 out/boot_app0.bin \
  0x10000 out/firmware.bin
```

> `src/main.cpp` 是 Arduino IDE 之外的标准写法。arduino-cli 要求 sketch 目录下有同名 `.ino`，
> 所以 CI 里会先把 `src/main.cpp` 复制成 `<目录名>.ino` 再编译（见 `.github/workflows/firmware.yml`）。

### 依赖

`lib/` 已内置，无需联网：

- `lib/ESP32-BLE-HID` — 蓝牙 HID 组合设备（键盘 + 绝对坐标触摸），报告描述符 Logical Max = 10000
- `lib/ArduinoJson` — JSON 解析（v6.21.x）

### 分区表

```
nvs       0x9000    20KB
otadata   0xe000    8KB     ← 记录当前从哪个 app 分区启动
app0      0x10000   1920KB  ← ota_0
app1      0x1F0000  1920KB  ← ota_1
spiffs    0x3D0000  128KB
coredump  0x3F0000  64KB
```

---

## 四、协议

### UDP（端口 21115）

第 1 字节是命令字，后面是 JSON 载荷。

| 命令 | 载荷 | 说明 |
| --- | --- | --- |
| `0x06` | `{"a":[x1,y1,x2,y2,...],"b":300,"c":1080,"d":2400}` | 手势；`a` 为扁平像素坐标，`b` 时长ms，`c/d` 手机屏幕宽高 |
| `0x07` | `{"k":"BACK","n":1,"i":60}` | 按键；`k` 见下表，`n` 次数，`i` 间隔ms |
| `0x08` | `{"t":"hello","i":30}` | 键盘逐字输入（仅 ASCII） |
| `0x09` | `{}` | 抬起复位，清掉可能残留的"按着"状态 |

坐标换算：像素 → 千分比（`0~10000`），即 `px * 10000 / 屏宽`。

按键名与 HID 用法：

| 名称 | 类型 | Usage |
| --- | --- | --- |
| `BACK` | Consumer | 0x0224 |
| `HOME` | Consumer | 0x0223 |
| `APP_SWITCH` | Consumer | 0x0221 |
| `VOLUME_UP` / `VOLUME_DOWN` / `MUTE` | Consumer | 0x00E9 / 0x00EA / 0x00E2 |
| `MEDIA_PLAY_PAUSE` / `NEXT` / `PREV` | Consumer | 0x00CD / 0x00B5 / 0x00B6 |
| `ENTER` / `ESC` / `TAB` | Keyboard | 0xB0 / 0xB1 / 0xB3 |
| `POWER` | Consumer | 0x0030（Android 通常会忽略） |

### HTTP（端口 80）

| 路径 | 方法 | 说明 |
| --- | --- | --- |
| `/` | GET | 状态页：版本、WiFi 模式、IP、蓝牙连接、手势计数、最近错误；含配网与升级表单 |
| `/ping` | GET | 探活，返回 `pong 1.1.0`。App 用它判断在线与版本 |
| `/info` | GET | JSON：`fw/chip/type/name/ble/ip/gestures/commands/uptime/ota/cmds` |
| `/wifi` | POST | 表单 `ssid`/`pass`，保存后重启；连不上自动回退热点 |
| `/update` | POST | OTA，`multipart/form-data`，字段名 `firmware`，文件名必须以 `.bin` 结尾 |

**热点**：连不上 WiFi 时自动开热点，名称为 `AutoJS HID-XXXX`（XXXX 取 MAC 末两字节），
密码 `autojs1234`，设备 IP 固定 `192.168.4.1`。

**OTA 鉴权**：`/update` 接受 `Authorization: Basic base64("admin:autojs1234")`。
默认配置下不带认证头也放行（兼容浏览器直传），要强制校验把 `OTA_ALLOW_NO_AUTH` 改成 `0`。

---

## 五、注意事项

- 固件大小上限 1.92MB（app0/app1 各 1920KB）。当前约 1.3MB，还有富余；加库时注意别超。
- `0x07` / `0x08` / `0x09` 是新指令。刷回原厂固件后，App 里这几个动作会发出去但无响应，
  点击类动作（`0x06`）不受影响。
- 蓝牙配对名沿用 MAC 后缀规则，**首次刷机后手机上可能要多点一次"配对"**，
  原来的配对记录对不上新固件的服务结构。之后 OTA 不会再变。
