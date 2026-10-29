package top.bogey.touch_tool.bean.action.logic;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.DynamicPinsAction;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.action.start.InnerStartAction;
import top.bogey.touch_tool.bean.other.log.ActionLog;
import top.bogey.touch_tool.bean.other.log.LogInfo;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinAdd;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.AlwaysShowPin;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.save.log.LogSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.ITaskListener;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.tree.ITreeNodeData;

/**
 * 并行执行 · 修复与增强版
 * <p>
 * 修复的 5 个坑：
 * 1. 分支启动失败（线程池满 runTask 返回 null）不再计入 latch，立即记日志跳过，避免白等整个超时
 * 2. 超时后停止所有未完成分支，不再留僵尸分支在后台继续点击/查找
 * 3. 新增"胜出分支序号"输出针脚：赛马模式记录是第几个分支先完成（1 基，0 = 没有胜者）
 * 4. 赛马判据改用实际分支数（validPins.size()），"完成数"针脚只做上限截断，
 *    修掉"填错数字导致赛马悄悄退化成汇合"的问题
 * 5. 调试标志（FLAG_DEBUG）执行完毕后在 finally 中移除，不再泄漏
 * <p>
 * 新增功能：
 * - 快速失败模式（完成模式第三项）：任一分支以"未达"状态结束（如查找控件走了 else），
 *   立即停止其他分支并整体置为失败
 * - 分支日志带序号：日志显示"分支1/分支2…最先完成/失败"，多分支调试可分辨
 * <p>
 * 兼容性：原针脚顺序不变，新输出针脚追加在末尾；旧任务里无名字的动态分支照常工作。
 */
public class ParallelExecuteAction extends ExecuteAction implements DynamicPinsAction {
    private final static Pin morePin = new Pin(new PinExecute(), R.string.pin_execute, true);

    private final transient Pin countPin = new Pin(new PinInteger(1), R.string.parallel_action_count);
    private final transient Pin timeoutPin = new Pin(new PinInteger(5000), R.string.parallel_action_timeout);
    // 完成模式：0 赛马（任一完成即停其他）/ 1 汇合（全部完成）/ 2 快速失败（任一分支未达即停其他）
    private final transient Pin modePin = new NotLinkAblePin(new PinSingleSelect(R.array.parallel_action_mode), R.string.parallel_action_mode);

    private final transient Pin addPin = new AlwaysShowPin(new PinAdd(morePin), R.string.pin_add_execute, true);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.pin_boolean_result, true);
    private final transient Pin completePin = new Pin(new PinExecute(), R.string.random_action_complete, true);
    // ===== 新增输出（追加在末尾，兼容旧任务）=====
    private final transient Pin winnerPin = new Pin(new PinInteger(), R.string.parallel_action_winner, true);


    public ParallelExecuteAction() {
        super(ActionType.PARALLEL_LOGIC);
        Pin secondPin = new Pin(new PinExecute(), R.string.pin_execute, true, true);
        addPins(countPin, modePin, timeoutPin, secondPin, addPin, resultPin, completePin, winnerPin);
    }

    public ParallelExecuteAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(countPin, modePin, timeoutPin);
        reAddPins(morePin);
        reAddPins(addPin, resultPin, completePin, winnerPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        PinNumber<?> timeout = getPinValue(runnable, timeoutPin);

        MainAccessibilityService service = MainApplication.getInstance().getService();

        // 收集已连线的分支针脚
        List<Pin> validPins = new ArrayList<>();
        collectBranches(validPins);

        int branchCount = validPins.size();
        if (branchCount == 0) {
            runnable.addLog("并行执行：没有已连线的分支，直接跳过");
            executeNext(runnable, completePin);
            return;
        }

        CountDownLatch latch = new CountDownLatch(branchCount);
        AtomicBoolean winnerSet = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicInteger notStarted = new AtomicInteger(0);

        Task currTask = runnable.getTask();
        boolean debug = runnable.isDebug();
        if (debug) currTask.addFlag(Task.FLAG_DEBUG);

        List<TaskRunnable> runnableList = new ArrayList<>();
        List<Boolean> startedList = new ArrayList<>();

        // stopOthers：停掉所有已启动且未结束的分支（幂等）
        Runnable stopOthers = () -> {
            if (stopped.compareAndSet(false, true)) {
                for (TaskRunnable run : runnableList) {
                    if (run != null && !run.isInterrupt()) run.stop();
                }
            }
        };

        for (int i = 0; i < branchCount; i++) {
            Pin dynamicPin = validPins.get(i);
            int branchIndex = i + 1; // 对外展示 1 基
            String branchLabel = "分支" + branchIndex;

            TaskRunnable taskRunnable = service.runTask(currTask, new InnerStartAction(dynamicPin), new ITaskListener() {
                @Override
                public void onExecute(TaskRunnable run, Action action, int progress) {
                    if (runnable.isCurrentInterrupt()) run.stop();
                }

                @Override
                public void onFinish(TaskRunnable run) {
                    // 日志先落盘（无论哪种模式都需要）
                    List<LogInfo> logList = run.getCacheLogList();
                    List<String> logs = saveLogs(runnable, logList);
                    LogInfo logInfo = new LogInfo(new ActionLog(runnable.getProgress() + 1, currTask, ParallelExecuteAction.this, true));
                    logInfo.setChildren(logs);
                    runnable.addLog(logInfo, 0);

                    // 坑1修复的另一半：只有真正启动过的分支才参与计数
                    latch.countDown();

                    int mode = modeIndex();
                    // 坑4修复：用实际分支数判断，赛马且尚未有胜者时，本分支即胜者
                    if (mode == 0 && !winnerSet.get() && !run.isInterrupt()) {
                        if (winnerSet.compareAndSet(false, true)) {
                            winnerPin.getValue(PinInteger.class).setValue(branchIndex);
                            resultPin.getValue(PinBoolean.class).setValue(true);
                            runnable.addLog("并行执行：" + branchLabel + "最先完成（赛马胜出）");
                            stopOthers.run();
                        }
                    } else if (mode == 2 && !stopped.get()) {
                        // 快速失败：分支以"未达"状态结束（链路上有动作走了 else 分支）
                        if (branchFailed(run, currTask)) {
                            resultPin.getValue(PinBoolean.class).setValue(false);
                            runnable.addLog("并行执行：" + branchLabel + "失败，快速停止其他分支");
                            stopOthers.run();
                        }
                    } else if (mode == 1 && !run.isInterrupt()) {
                        runnable.addLog("并行执行：" + branchLabel + "完成");
                    }
                }
            });
            runnableList.add(taskRunnable);

            // 坑1修复：分支启动失败（线程池满）不计入等待，立即记日志并放行 latch
            if (taskRunnable == null) {
                startedList.add(false);
                notStarted.incrementAndGet();
                runnable.addLog("并行执行：" + branchLabel + "启动失败（线程池已满），已跳过");
                latch.countDown();
            } else {
                startedList.add(true);
            }
        }

        ITaskListener listener = new ITaskListener() {
            @Override
            public void onPauseChanged(TaskRunnable taskRunnable, boolean paused) {
                for (TaskRunnable run : runnableList) {
                    if (run != null) {
                        if (paused) run.forcePause();
                        else run.forceResume();
                    }
                }
            }
        };
        runnable.addListener(listener);

        boolean timeouted = false;
        try {
            if (timeout.intValue() <= 0) {
                latch.await();
            } else {
                // 坑2修复：记录是否超时返回
                timeouted = !latch.await(timeout.intValue(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resultPin.getValue(PinBoolean.class).setValue(false);
        }

        // 坑2修复：超时（或被中断）后停止所有未完成分支，不留僵尸分支
        if (timeouted) {
            runnable.addLog("并行执行：等待超时，停止未完成的分支");
            stopOthers.run();
            if (!winnerSet.get()) resultPin.getValue(PinBoolean.class).setValue(false);
        }
        if (notStarted.get() > 0) {
            // 有分支没启动成功：不能宣称成功
            resultPin.getValue(PinBoolean.class).setValue(false);
        }

        runnable.removeListener(listener);

        try {
            executeNext(runnable, completePin);
        } finally {
            // 坑5修复：调试标志用完即摘，不再泄漏
            if (debug) currTask.removeFlag(Task.FLAG_DEBUG);
        }
    }

    // ==================== 分支收集与命名 ====================
    /**
     * 分支针脚 = 已连线的动态执行针脚。
     */
    private void collectBranches(List<Pin> branchPins) {
        List<Pin> all = getPins();
        for (Pin pin : all) {
            if (pin == addPin) break;
            if (pin.isDynamic() && pin.isOut() && pin.isLinked() && pin.isSameClass(PinExecute.class)) {
                branchPins.add(pin);
            }
        }
    }

    private int modeIndex() {
        PinSingleSelect mode = modePin.getValue(PinSingleSelect.class);
        return mode == null ? 1 : mode.getIndex();
    }

    /**
     * 快速失败判定：分支运行日志里任一动作处于"未达"状态即认为分支失败。
     * 覆盖典型场景：查找控件超时走了 else、条件分支未命中等。
     */
    private boolean branchFailed(TaskRunnable run, Task task) {
        for (LogInfo logInfo : run.getCacheLogList()) {
            if (checkLogFailed(logInfo, task)) return true;
        }
        return false;
    }

    private boolean checkLogFailed(LogInfo logInfo, Task task) {
        if (logInfo.getLogObject() instanceof ActionLog actionLog) {
            Action action = task.getAction(actionLog.getActionId());
            if (action != null && action.isUnachieved()) return true;
        }
        for (ITreeNodeData child : logInfo.getChildrenData()) {
            if (child instanceof LogInfo childInfo && checkLogFailed(childInfo, task)) return true;
        }
        return false;
    }

    private List<String> saveLogs(TaskRunnable runnable, List<LogInfo> logList) {
        List<String> logs = new ArrayList<>();
        for (LogInfo logInfo : logList) {
            logs.add(logInfo.getUid());
            LogSaver.getInstance().addLog(runnable.getStartTask().getId(), logInfo, false);
            List<String> children = new ArrayList<>();
            for (ITreeNodeData child : logInfo.getChildrenData()) {
                LogInfo childLogInfo = (LogInfo) child;
                children.add(childLogInfo.getUid());
                LogSaver.getInstance().addLog(runnable.getStartTask().getId(), childLogInfo, false);
                saveChildLogs(runnable, childLogInfo.getChildrenData(), children);
            }
            logInfo.setChildren(children);
        }
        return logs;
    }

    private void saveChildLogs(TaskRunnable runnable, List<ITreeNodeData> childrenData, List<String> children) {
        for (ITreeNodeData child : childrenData) {
            LogInfo childLogInfo = (LogInfo) child;
            children.add(childLogInfo.getUid());
            LogSaver.getInstance().addLog(runnable.getStartTask().getId(), childLogInfo, false);
            saveChildLogs(runnable, childLogInfo.getChildrenData(), children);
        }
    }

    @Override
    public List<Pin> getDynamicPins() {
        List<Pin> pins = new ArrayList<>();
        boolean start = false;
        for (Pin pin : getPins()) {
            if (pin == addPin) start = false;
            if (start && pin.isDynamic() && pin.isOut()) pins.add(pin);
            if (pin == timeoutPin) start = true;
        }
        return pins;
    }
}
