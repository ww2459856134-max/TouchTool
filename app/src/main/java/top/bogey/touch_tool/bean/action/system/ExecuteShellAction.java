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

    public ExecuteShellAction() {
        super(ActionType.SHELL);
        addPins(cmdPin, timeoutPin, successPin, outputPin, elsePin);
    }

    public ExecuteShellAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(cmdPin, timeoutPin, successPin, outputPin, elsePin);
        // 高级参数固定归入第二页
        timeoutPin.setHide(true);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String cmd = getPinValue(runnable, cmdPin).toString();
        successPin.getValue(PinBoolean.class).setValue(false);
        outputPin.setValue(new PinString(""));

        if (cmd.isEmpty()) {
            executeNext(runnable, elsePin);
            return;
        }
        runShell(runnable, cmd, true);
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
                outputPin.setValue(new PinString("命令执行超时"));
                executeNext(runnable, elsePin);
                return;
            }

            if (cmdResult != null) {
                outputPin.getValue(PinString.class).setValue(cmdResult.getOutput());
                successPin.getValue(PinBoolean.class).setValue(cmdResult.getResult());
                executeNext(runnable, cmdResult.getResult() ? outPin : elsePin);
                return;
            }
            executeNext(runnable, elsePin);
            return;
        }

        // 通道未初始化：先尝试初始化，成功后重试一次
        if (allowInit) {
            if (superUser instanceof SuperUser) {
                // 内置空实现（未配置通道）无法初始化，直接引导配置
                showChannelGuide();
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
            executeNext(runnable, elsePin);
            return;
        }

        showChannelGuide();
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
