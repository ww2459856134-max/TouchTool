# TouchTool HID 固件 v1.5.0（修复+增强版）

基于 TouchTool 固件 v1.2.9 源码的修复与增强版本。

## 版本历史
- v1.2.9-fix1：P0×4 + P1×9 修复
- v1.3.0：状态页增强、UDP 白名单、WiFi 扫描、自定义设备名
- v1.3.1：修复网页 OTA 必失败
- v1.4.0：mDNS 域名访问
- v1.5.0：鼠标滚轮（UDP 指令 0x0A + App「HID滚轮」动作卡）

## 编译
Arduino CLI + esp32 core 3.3.12（NimBLE），见上方命令。
配套 App 改动（HID滚轮动作卡）在本仓库 fw-1.5.0 分支的 app/ 下。
