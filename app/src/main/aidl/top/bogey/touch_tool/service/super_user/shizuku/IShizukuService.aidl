// IShizukuService.aidl
package top.bogey.touch_tool.service.super_user.shizuku;

import top.bogey.touch_tool.service.super_user.CmdResult;

interface IShizukuService {
    void destory() = 16777114;
    CmdResult runCommand(String cmd) = 1;
}