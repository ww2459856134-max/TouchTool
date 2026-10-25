package top.bogey.touch_tool.utils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import top.bogey.touch_tool.bean.save.setting.SettingSaver;
import top.bogey.touch_tool.service.super_user.CmdResult;
import top.bogey.touch_tool.service.super_user.ISuperUser;
import top.bogey.touch_tool.service.super_user.SuperUser;

// Shell 命令执行工具：带超时保护、通道自动初始化，供各动作复用
public class ShellRunner {
    // 返回 null 表示：通道不可用 / 超时 / 执行异常（result 字段区分命令本身失败）
    public static CmdResult run(String cmd, long timeoutMs) {
        ISuperUser superUser = SuperUser.getInstance();
        if (!superUser.isValid()) {
            // 尝试初始化一次（Root 通道同步生效；Shizuku 异步，这里只尽力而为）
            if (superUser instanceof SuperUser) return null;
            final boolean[] inited = {false};
            superUser.init(result -> inited[0] = result);
            if (!inited[0]) return null;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CmdResult> future = executor.submit(() -> superUser.runCommand(cmd));
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 超时关会话止损，下次执行自动重连
            superUser.exit();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        } finally {
            executor.shutdownNow();
        }
    }

    // 通道是否已配置（设置里选了 Shizuku 或 Root）
    public static boolean channelConfigured() {
        return SettingSaver.PERMISSION_SUPER_USER.get() != 0;
    }

    public static String channelError() {
        return channelConfigured() ? "通道未就绪或命令超时" : "请先在应用设置中配置 Shizuku 或 Root 通道";
    }
}
