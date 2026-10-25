package top.bogey.touch_tool.bean.action.system;

import android.widget.Toast;

import com.google.gson.JsonObject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionCheckResult;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.save.setting.SettingSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.service.super_user.CmdResult;
import top.bogey.touch_tool.service.super_user.ISuperUser;
import top.bogey.touch_tool.service.super_user.SuperUser;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.callback.BooleanResultCallback;

// Shell 命令执行：通过 Shizuku 或 Root 通道执行命令
// 支持多行脚本、超时保护、首次自动初始化重试、未配置通道时引导
public class ExecuteShellAction extends ExecuteAction {
    // 注意：命令针脚必须保持 PinString，换成子类会因 isSameClass 精确匹配导致老任务的值和连线丢失
    private final transient Pin cmdPin = new Pin(new PinString(), R.string.execute_shell_action_cmd);
    private final transient Pin timeoutPin = new NotLinkAblePin(new PinInteger(10000), R.string.execute_shell_action_timeout, false, false, true);
    private final transient Pin successPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    private final transient Pin outputPin = new Pin(new PinString(), R.string.execute_shell_action_output, true);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.execute_shell_action_else, true);
    // 以下新针脚追加在末尾，旧任务按位置同步不受影响
    // 工作目录：填了就在该目录下执行；整条命令包在子 shell 里，cd/export 的副作用不会漏到别的任务
    private final transient Pin dirPin = new Pin(new PinString(), R.string.execute_shell_action_dir);
    // 命令退出码：0 表示成功；-1 表示未知（超时/通道异常）
    private final transient Pin exitCodePin = new Pin(new PinInteger(0), R.string.execute_shell_action_exit_code, true);
    // 后台执行：勾选后命令发出去就走，不等待、不返回输出
    private final transient Pin asyncPin = new NotLinkAblePin(new PinBoolean(false), R.string.execute_shell_action_async);
    // 命令模板：命令框留空时按所选模板执行
    private final transient Pin templatePin = new NotLinkAblePin(new PinSingleSelect(R.array.shell_cmd_template), R.string.execute_shell_action_template);

    public ExecuteShellAction() {
        super(ActionType.SHELL);
        addPins(cmdPin, timeoutPin, successPin, outputPin, elsePin, dirPin, exitCodePin, asyncPin, templatePin);
    }

    public ExecuteShellAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(cmdPin, timeoutPin, successPin, outputPin, elsePin, dirPin, exitCodePin, asyncPin, templatePin);
        // 高级参数固定归入第二页
        timeoutPin.setHide(true);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String cmd = getPinValue(runnable, cmdPin).toString();
        String dir = getPinValue(runnable, dirPin).toString().trim();
        successPin.getValue(PinBoolean.class).setValue(false);
        outputPin.setValue(new PinString(""));
        exitCodePin.getValue(PinInteger.class).setValue(-1);

        // 命令框留空时用所选模板兜底
        if (cmd.isEmpty()) {
            int template = 0;
            if (getPinValue(runnable, templatePin) instanceof PinSingleSelect select) template = select.getIndex();
            String[] cmds = MainApplication.getInstance().getResources().getStringArray(R.array.shell_cmd_template_cmds);
            if (template > 0 && template - 1 < cmds.length) cmd = cmds[template - 1];
        }

        if (cmd.isEmpty()) {
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        // 后台执行：通道就绪时直接把命令扔给独立线程，立即走成功线，不等待、不返回输出
        if (getPinValue(runnable, asyncPin) instanceof PinBoolean pinBoolean && pinBoolean.getValue()
                && SuperUser.getInstance().isValid()) {
            ISuperUser superUserAsync = SuperUser.getInstance();
            String finalCmd = cmd;
            new Thread(() -> superUserAsync.runCommand("( " + finalCmd + "\n )"), "shell-async").start();
            successPin.getValue(PinBoolean.class).setValue(true);
            outputPin.setValue(new PinString("命令已提交后台执行（不等待、无输出）"));
            executeNext(runnable, outPin);
            return;
        }

        // 整条命令包进子 shell：( ... )
        // 1) 命令里的 cd / export 只影响本次执行，不会漏到会话里影响其他任务
        // 2) 填了工作目录就先 cd 过去（目录名里的单引号做转义）
        StringBuilder wrapped = new StringBuilder("( ");
        if (!dir.isEmpty()) {
            wrapped.append("cd '").append(dir.replace("'", "'\\''")).append("' &&\n");
        }
        wrapped.append(cmd).append("\n )");
        runShell(runnable, wrapped.toString(), true);
    }

    // allowInit: 是否允许触发通道初始化（防重复递归）
    private void runShell(TaskRunnable runnable, String cmd, boolean allowInit) {
        ISuperUser superUser = SuperUser.getInstance();
        if (superUser.isValid()) {
            long timeout = 10000;
            if (getPinValue(runnable, timeoutPin) instanceof PinInteger pinInteger) {
                timeout = Math.max(1000, pinInteger.getValue());
            }

            // 独立线程执行命令，主任务线程限时等待，防止命令卡死阻塞任务
            ExecutorService executor = Executors.newSingleThreadExecutor();
            CmdResult cmdResult = null;
            boolean timeoutHappened = false;
            try {
                Future<CmdResult> future = executor.submit(() -> superUser.runCommand(cmd));
                cmdResult = future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                timeoutHappened = true;
                // 超时后关闭会话以终止卡死的命令，下次执行会自动重连
                superUser.exit();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ignored) {
            } finally {
                executor.shutdownNow();
            }

            if (timeoutHappened) {
                exitCodePin.getValue(PinInteger.class).setValue(-1);
                // 超时后关闭会话以终止卡死的命令，并立刻尝试重连，避免下一条命令再付一次连接开销
                superUser.exit();
                final boolean[] reconnected = {false};
                superUser.init(result -> reconnected[0] = result);
                outputPin.setValue(new PinString("命令执行超时（超时上限 " + timeout + " 毫秒）" +
                        (reconnected[0] ? "，通道已自动重连" : "，通道将在下次执行时自动重连")));
                markUnachieved();
                executeNext(runnable, elsePin);
                return;
            }

            if (cmdResult != null) {
                outputPin.getValue(PinString.class).setValue(cmdResult.getOutput());
                exitCodePin.getValue(PinInteger.class).setValue(cmdResult.getExitCode());
                successPin.getValue(PinBoolean.class).setValue(cmdResult.getResult());
                if (!cmdResult.getResult()) {
                    // 失败时把人话原因附到输出末尾，方便排查
                    String reason;
                    if (cmdResult.getExitCode() >= 0) {
                        reason = "命令执行失败（退出码 " + cmdResult.getExitCode() + "）";
                    } else {
                        reason = "命令执行失败（" + cmdResult.getOutput() + "）";
                    }
                    String output = cmdResult.getOutput();
                    outputPin.setValue(new PinString(output == null || output.isEmpty()
                            ? reason : output + "\n" + reason));
                }
                executeNext(runnable, cmdResult.getResult() ? outPin : elsePin);
                return;
            }
            exitCodePin.getValue(PinInteger.class).setValue(-1);
            outputPin.setValue(new PinString("命令执行失败（通道异常，命令没有返回结果）"));
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        // 通道未初始化：先尝试初始化，成功后重试一次
        if (allowInit) {
            if (superUser instanceof SuperUser) {
                // 内置空实现（未配置通道）无法初始化，直接引导配置
                showChannelGuide();
                markUnachieved();
                executeNext(runnable, elsePin);
                return;
            }
            final boolean[] inited = {false};
            superUser.init(result -> inited[0] = result);
            if (inited[0]) {
                runShell(runnable, cmd, false);
                return;
            }
            showChannelGuide();
            markUnachieved();
            executeNext(runnable, elsePin);
            return;
        }

        showChannelGuide();
        markUnachieved();
        executeNext(runnable, elsePin);
    }

    // 未配置 Shizuku / Root 通道时提示用户去设置
    private void showChannelGuide() {
        if (SettingSaver.PERMISSION_SUPER_USER.get() == 0) {
            AppUtil.runOnUiThread(() -> Toast.makeText(MainApplication.getInstance(),
                    R.string.execute_shell_action_channel_tips, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void check(ActionCheckResult result, Task task) {
        super.check(result, task);
        if (!SuperUser.getInstance().isValid()) {
            result.addResult(ActionCheckResult.ResultType.ERROR, R.string.check_need_super_user_error);
        }
    }
}
