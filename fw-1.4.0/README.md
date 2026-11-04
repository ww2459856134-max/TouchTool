# TouchTool HID 固件 v1.4.0（修复+增强版）

基于 TouchTool 固件 v1.2.9 源码的修复与增强版本。

## 版本历史
- v1.2.9-fix1：P0×4 + P1×9 修复（hidSent 统计、BLE 连接事件、芯片名、sendText 非 ASCII、
  waitYield 让长手势不阻塞 HTTP/UDP、手势绝对时间轴、环形 trace、OTA multipart 精确预检等）
- v1.3.0：状态页增强（运行时长/空闲堆/重启原因）、UDP 来源白名单、WiFi 扫描、自定义设备名
- v1.3.1：修复网页 OTA 必失败（multipart 开销估算差 1~2 字节导致字节级校验恒不通过，
  改为 ±512B 容差 + Update.end() 校验和/SHA256 把关）
- v1.4.0：mDNS 域名访问（http://touchtool.local）

## 编译
Arduino CLI + esp32 core 3.3.12（NimBLE）：
```bash
arduino-cli core install esp32:esp32@3.3.12
arduino-cli compile --fqbn "esp32:esp32:esp32s3:FlashSize=16M,PartitionScheme=custom" \
  --library lib/ESP32-BLE-HID --output-dir out src
# C3: --fqbn "esp32:esp32:esp32c3:FlashSize=4M,PartitionScheme=custom"
```
注意：需对 core 应用 NimBLE UUID 去重补丁（patches 思路见原仓库
firmware/esp32c3-hid/patches/apply-core-patch.py）。

## 目录
- src/main.cpp      固件全部源码（单文件）
- lib/ESP32-BLE-HID BLE 键鼠复合 HID 库（NimBLE 原生）
- stub/ + verify_syntax.sh  离线语法校验（无需安装 core 即可 g++ 检查）
- partitions.csv    双 app 分区表（OTA 前提）
