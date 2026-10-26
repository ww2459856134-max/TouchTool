package top.bogey.touch_tool.bean.action.node;

import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.PinNode;
import top.bogey.touch_tool.bean.pin.pin_objects.PinObject;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

// 输入框输入：向输入框输入文本或粘贴剪贴板内容（原输入框输入文本与输入框粘贴合并）
//
// 性能要点：
// 1. 按 ID 定位时直接用系统 API 单点查找，不再遍历整棵树
// 2. 深度上限 MAX_DEPTH，避免超大页面把整棵树拉完
// 3. 多个窗口并行扫描，最多 PARALLEL_WINDOW_THREADS 个线程
// 4. 序号负值（-1 取最后一个）从树尾部倒序扫，命中即停
public class EditTextInputAction extends ExecuteAction {
    // 输入框定位方式，与 R.array.edit_text_locate_mode 一一对应
    private static final int LOCATE_INDEX = 0;          // 按序号查找
    private static final int LOCATE_FOCUSED = 1;        // 自动：已聚焦输入框
    private static final int LOCATE_TEXT = 2;           // 自动：文本匹配
    private static final int LOCATE_DESC = 3;           // 自动：描述匹配
    private static final int LOCATE_ID = 4;             // 自动：ID 匹配

    // 失败兜底方式，与 R.array.edit_text_fallback 一一对应
    private static final int FALLBACK_NONE = 0;
    private static final int FALLBACK_GESTURE = 1;

    // 遍历深度上限：普通页面 12-15 层足够，设上限避免把整棵树都实例化出来
    private static final int MAX_DEPTH = 16;
    // 并行扫描窗口的最大线程数
    private static final int PARALLEL_WINDOW_THREADS = 3;
    // 单次并行扫描的等待上限，超时就用已收集到的结果继续
    private static final long PARALLEL_TIMEOUT_MS = 1000;

    private final transient Pin nodePin = new Pin(new PinNode(), R.string.edit_text_input_action_edit_text, false, false, true);
    private final transient Pin locateModePin = new NotLinkAblePin(new PinSingleSelect(R.array.edit_text_locate_mode), R.string.edit_text_input_action_locate_mode);
    private final transient Pin indexPin = new IndexShowablePin(new PinInteger(0), R.string.edit_text_input_action_index);
    private final transient Pin locateValuePin = new LocateValueShowablePin(new PinString(), R.string.edit_text_input_action_locate_value);
    private final transient Pin modePin = new NotLinkAblePin(new PinSingleSelect(R.array.edit_text_input_mode), R.string.edit_text_input_action_mode);
    private final transient Pin contentPin = new ContentShowablePin(new PinString(), R.string.pin_string, false, false, true);
    private final transient Pin appendPin = new ContentShowablePin(new PinBoolean(), R.string.edit_text_input_action_append, false, false, true);
    private final transient Pin clearFirstPin = new ContentShowablePin(new PinBoolean(), R.string.edit_text_input_action_clear_first, false, false, true);
    private final transient Pin enterPin = new EnterShowablePin(new PinBoolean(), R.string.edit_text_input_action_enter, false, false, true);
    private final transient Pin fallbackPin = new NotLinkAblePin(new PinSingleSelect(R.array.edit_text_fallback), R.string.edit_text_input_action_fallback);
    private final transient Pin elsePin = new Pin(new PinExecute(), R.string.node_touch_action_else, true);

    public EditTextInputAction() {
        this(ActionType.EDITTEXT_INPUT);
    }

    public EditTextInputAction(ActionType type) {
        super(type);
        addPins(nodePin, locateModePin, indexPin, locateValuePin, modePin, contentPin, appendPin, clearFirstPin, enterPin, fallbackPin, elsePin);
        // 兼容旧的输入框粘贴动作，默认为粘贴模式
        if (type == ActionType.EDITTEXT_PASTE) modePin.getValue(PinSingleSelect.class).setIndex(1);
    }

    public EditTextInputAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(nodePin, locateModePin, indexPin, locateValuePin, modePin, contentPin, appendPin, clearFirstPin, enterPin, fallbackPin, elsePin);
        // 兼容旧的输入框粘贴动作，默认为粘贴模式
        if (getType() == ActionType.EDITTEXT_PASTE) modePin.getValue(PinSingleSelect.class).setIndex(1);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        NodeInfo nodeInfo = null;
        // 1. 优先使用连线的输入框
        if (nodePin.isLinked()) {
            PinNode node = getPinValue(runnable, nodePin);
            if (node != null) nodeInfo = node.getNodeInfo();
        }
        // 2. 未连线（或连线为空）时按定位方式自动查找
        if (nodeInfo == null) {
            nodeInfo = locateEditText(runnable);
        }
        // 3. 还是找不到，按兜底方式处理
        if (nodeInfo == null && getFallbackMode(runnable) == FALLBACK_GESTURE) {
            nodeInfo = gestureFallback(runnable);
        }

        boolean result = false;
        if (nodeInfo != null && nodeInfo.node != null) {
            result = doInput(runnable, nodeInfo);
        }
        executeNext(runnable, result ? outPin : elsePin);
    }

    // 执行完整的输入流程：聚焦 -> 清空 -> 写入 -> 回车
    private boolean doInput(TaskRunnable runnable, NodeInfo nodeInfo) {
        AccessibilityNodeInfo node = nodeInfo.node;
        if (node == null) return false;

        // 聚焦：只有本身可聚焦且尚未聚焦时才做（已聚焦再发一次纯属浪费一次 IPC）
        if (node.isFocusable() && !node.isFocused()) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        }

        // 先清空再输入：用一次 SET_TEXT 空串实现，比 DELETE 更可靠
        boolean clearFirst = getBooleanValue(runnable, clearFirstPin);
        if (clearFirst) {
            Bundle clearBundle = new Bundle();
            clearBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearBundle);
        }

        PinSingleSelect mode = getPinValue(runnable, modePin);
        boolean result;
        boolean pasteMode = mode != null && mode.getIndex() == 1;
        if (pasteMode) {
            // 粘贴剪贴板内容：不清空时先把光标移到末尾，避免粘贴到中间
            if (!clearFirst) {
                Bundle moveBundle = new Bundle();
                moveBundle.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                        AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE);
                moveBundle.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false);
                node.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, moveBundle);
            }
            result = node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
        } else {
            result = setText(runnable, node);
        }

        // 回车：SET_TEXT 之后部分 App 需要显式触发才提交（粘贴通常自带换行，不再补发）
        if (result && !pasteMode
                && getBooleanValue(runnable, enterPin)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
        }
        return result;
    }

    // 写入文本，必要时与实时内容拼接
    private boolean setText(TaskRunnable runnable, AccessibilityNodeInfo node) {
        PinObject content = getPinValue(runnable, contentPin);
        String contentValue = content == null ? "" : content.toString();

        if (getBooleanValue(runnable, appendPin)) {
            // 追加模式必须读实时内容，NodeInfo.text 是构造时的快照，会用旧值覆盖掉新输入
            CharSequence current = node.getText();
            String prefix = current == null ? "" : current.toString();
            contentValue = prefix + contentValue;
        }

        Bundle bundle = new Bundle();
        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, contentValue);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);
    }

    // ================= 定位 =================

    private NodeInfo locateEditText(TaskRunnable runnable) {
        PinSingleSelect modePinValue = getPinValue(runnable, locateModePin);
        int mode = modePinValue == null ? LOCATE_INDEX : modePinValue.getIndex();

        // ID 定位直接问系统，一次 IPC 就够，不用遍历
        if (mode == LOCATE_ID) {
            PinObject value = getPinValue(runnable, locateValuePin);
            String keyword = value == null ? "" : value.toString();
            if (keyword.isEmpty()) return null;
            NodeInfo direct = findByIdDirect(keyword);
            if (direct != null) return direct;
            // 系统按完整 ID 没找到（用户填的是短 ID），才退回遍历做包含匹配
        }

        if (mode == LOCATE_INDEX) {
            int index = 0;
            if (getPinValue(runnable, indexPin) instanceof PinInteger pinInteger) index = pinInteger.getValue();
            // 序号统一用正序全量扫描：只有正序全量，序号语义才有确定含义。
            // 不做「负序号倒序扫」的优化——倒序命中的是深度优先的最后一个，与正序列表的末位不是同一个节点，
            // 省下的那点时间换不来序号错位，不划算。
            return scanEditTexts(ScanGoal.ALL, index, null, null);
        }

        if (mode == LOCATE_FOCUSED) {
            return scanEditTexts(ScanGoal.FOCUSED, 0, null, null);
        }

        PinObject value = getPinValue(runnable, locateValuePin);
        String keyword = value == null ? "" : value.toString();
        if (keyword.isEmpty()) return null;
        return scanEditTexts(ScanGoal.FIRST_MATCH, 0, mode, keyword);
    }

    // 用系统 API 按完整 ID 直接找输入框，避免遍历
    private NodeInfo findByIdDirect(String viewId) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(viewId);
        // 用户可能只填了 id/xxx 里的 xxx，补全常见包名前缀再试一次
        if (!viewId.contains("/") && !viewId.contains(":")) {
            NodeInfo active = NodeInfo.getActiveWindow();
            if (active != null && active.node != null) {
                CharSequence pkg = active.node.getPackageName();
                if (pkg != null) ids.add(pkg + ":id/" + viewId);
            }
        }

        for (String id : ids) {
            for (NodeInfo window : NodeInfo.getWindows()) {
                if (window == null || window.node == null) continue;
                List<AccessibilityNodeInfo> found = window.node.findAccessibilityNodeInfosByViewId(id);
                if (found == null) continue;
                for (AccessibilityNodeInfo node : found) {
                    if (node == null) continue;
                    try {
                        if (!node.isVisibleToUser()) continue;
                        if (node.isEditable()) return new NodeInfo(node);
                    } catch (Exception ignored) {
                        // 节点已回收，跳过
                    }
                }
            }
        }
        return null;
    }

    // 兜底方式下的查找：用手势点一下目标输入框，再看谁获得了焦点
    private NodeInfo gestureFallback(TaskRunnable runnable) {
        int index = 0;
        if (getPinValue(runnable, indexPin) instanceof PinInteger pinInteger) index = pinInteger.getValue();

        NodeInfo target = scanEditTexts(ScanGoal.ALL, index, null, null);
        if (target == null || target.area == null) return null;

        NodeActionHelper.tapCenter(target, runnable);

        // 点击后只看焦点，不用再全扫一遍
        NodeInfo focused = scanEditTexts(ScanGoal.FOCUSED, 0, null, null);
        return focused != null ? focused : target;
    }

    // ================= 扫描核心 =================

    // 扫描目标，决定能不能提前收工
    private enum ScanGoal {
        ALL,            // 要全部序号（按序号取值）
        FOCUSED,        // 只要已聚焦的
        FIRST_MATCH     // 只要第一个匹配关键字（文本/描述）
    }

    // 并行扫描所有窗口的输入框，按目标决定何时停
    private NodeInfo scanEditTexts(ScanGoal goal, int index, Integer matchMode, String keyword) {
        List<NodeInfo> windows = NodeInfo.getWindows();
        if (windows.isEmpty()) return null;

        // 单窗口（最常见）不开线程，直接扫，省掉线程调度开销
        if (windows.size() == 1) {
            List<NodeInfo> found = new ArrayList<>();
            scanWindow(windows.get(0), found, goal, matchMode, keyword, new AtomicBoolean(false));
            return pick(found, goal, index);
        }

        // 多窗口并行扫描
        ConcurrentLinkedQueue<NodeInfo> queue = new ConcurrentLinkedQueue<>();
        AtomicBoolean stopped = new AtomicBoolean(false);
        int threadCount = Math.min(windows.size(), PARALLEL_WINDOW_THREADS);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger cursor = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    while (!stopped.get()) {
                        int position = cursor.getAndIncrement();
                        if (position >= windows.size()) break;
                        List<NodeInfo> local = new ArrayList<>();
                        // 每个窗口独立的停止标志：命中只停本窗口，不能让别人漏扫
                        AtomicBoolean localStopped = new AtomicBoolean(false);
                        scanWindow(windows.get(position), local, goal, matchMode, keyword, localStopped);
                        queue.addAll(local);
                        // 只有「找到即可收工」的目标才让全局也停
                        if (localStopped.get() && canStopEarly(goal)) {
                            stopped.set(true);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }, "edittext-scan-" + i);
            thread.setDaemon(true);
            thread.start();
        }

        try {
            latch.await(PARALLEL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 超时也继续用已收集到的结果，不让界面卡死
        stopped.set(true);

        return pick(new ArrayList<>(queue), goal, index);
    }

    // 该目标命中后能否让其他窗口也停下
    private boolean canStopEarly(ScanGoal goal) {
        return goal == ScanGoal.FOCUSED || goal == ScanGoal.FIRST_MATCH;
    }

    // 扫描单个窗口。goal 允许提前收工时置位 stopped
    private void scanWindow(NodeInfo root, List<NodeInfo> out, ScanGoal goal,
                            Integer matchMode, String keyword, AtomicBoolean stopped) {
        if (root == null || stopped.get()) return;
        switch (goal) {
            case FOCUSED -> scanFocused(root, out, stopped, 0);
            case FIRST_MATCH -> scanFirstMatch(root, out, matchMode, keyword, stopped, 0);
            default -> scanAll(root, out, stopped, 0);
        }
    }

    // 正序扫，收集全部输入框
    private void scanAll(NodeInfo parent, List<NodeInfo> out, AtomicBoolean stopped, int depth) {
        if (parent == null || parent.node == null || depth > MAX_DEPTH || stopped.get()) return;
        if (isEditText(parent)) {
            out.add(parent);
            // 输入框内部不会再套输入框，不必向下钻
            return;
        }
        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            if (stopped.get()) return;
            scanAll(parent.getChild(i), out, stopped, depth + 1);
        }
    }

    // 只找已聚焦的输入框
    private boolean scanFocused(NodeInfo parent, List<NodeInfo> out, AtomicBoolean stopped, int depth) {
        if (parent == null || parent.node == null || depth > MAX_DEPTH || stopped.get()) return false;
        if (isEditText(parent)) {
            try {
                if (parent.node.isFocused()) {
                    out.add(parent);
                    stopped.set(true);
                    return true;
                }
            } catch (Exception ignored) {
                // 已回收
            }
            // 输入框本身没聚焦，不必往下钻
            return false;
        }
        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            if (scanFocused(parent.getChild(i), out, stopped, depth + 1)) return true;
        }
        return false;
    }

    // 只找第一个匹配关键字的输入框
    private boolean scanFirstMatch(NodeInfo parent, List<NodeInfo> out, Integer matchMode,
                                   String keyword, AtomicBoolean stopped, int depth) {
        if (parent == null || parent.node == null || depth > MAX_DEPTH || stopped.get()) return false;
        if (isEditText(parent)) {
            String target = matchMode == null ? null : switch (matchMode) {
                case LOCATE_TEXT -> parent.text;
                case LOCATE_DESC -> parent.desc;
                case LOCATE_ID -> parent.id;
                default -> null;
            };
            if (target != null && target.contains(keyword)) {
                out.add(parent);
                stopped.set(true);
                return true;
            }
            return false;
        }
        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            if (scanFirstMatch(parent.getChild(i), out, matchMode, keyword, stopped, depth + 1)) return true;
        }
        return false;
    }

    // 按扫描目标从结果里取值
    private NodeInfo pick(List<NodeInfo> found, ScanGoal goal, int index) {
        if (found == null || found.isEmpty()) return null;
        return switch (goal) {
            case FOCUSED, FIRST_MATCH -> found.get(0);
            default -> pickByIndex(found, index);
        };
    }

    // 序号取值：0 为第一个，-1 为最后一个，越界返回 null（交给 else 分支，不再静默归零）
    private NodeInfo pickByIndex(List<NodeInfo> list, int index) {
        if (list.isEmpty()) return null;
        if (index < 0) index = list.size() + index;
        if (index < 0 || index >= list.size()) return null;
        return list.get(index);
    }

    // 单节点判定输入框：以无障碍的 isEditable 为准，类名含 EditText 作兜底
    // 这样 AppCompatEditText / TextInputEditText / 自绘输入框都能命中，不再只认 android.widget.EditText
    private boolean isEditText(NodeInfo info) {
        if (info == null || info.node == null || !info.visible) return false;
        try {
            if (info.node.isEditable()) return true;
        } catch (Exception ignored) {
            // 部分 ROM 上节点已回收会抛异常，吞掉走类名兜底
        }
        // AppUtil.isStringContains 不判空，clazz 为空时会让 str.toLowerCase() 崩掉
        return info.clazz != null && AppUtil.isStringContains(info.clazz, "EditText");
    }

    private boolean getBooleanValue(TaskRunnable runnable, Pin pin) {
        PinBase value = getPinValue(runnable, pin);
        return value instanceof PinBoolean pinBoolean && pinBoolean.getValue();
    }

    private int getFallbackMode(TaskRunnable runnable) {
        PinSingleSelect fallback = getPinValue(runnable, fallbackPin);
        return fallback == null ? FALLBACK_NONE : fallback.getIndex();
    }

    // ================= 针脚可见性 =================

    // 当前选择的定位方式，供内部 ShowAblePin 查询
    int locateMode() {
        PinSingleSelect select = locateModePin.getValue();
        return select == null ? LOCATE_INDEX : select.getIndex();
    }

    // 序号针脚仅在「按序号查找」时显示（粘贴动作保留原始行为，不受定位方式影响）
    private static class IndexShowablePin extends ShowAblePin {
        public IndexShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            Action action = context.getAction(getOwnerId());
            if (!(action instanceof EditTextInputAction inputAction)) return false;
            if (inputAction.getType() == ActionType.EDITTEXT_PASTE) return true;
            return inputAction.locateMode() == LOCATE_INDEX;
        }
    }

    // 定位值针脚仅在文本/描述/ID 三种匹配方式下显示
    private static class LocateValueShowablePin extends ShowAblePin {
        public LocateValueShowablePin(PinBase value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            Action action = context.getAction(getOwnerId());
            if (!(action instanceof EditTextInputAction inputAction)) return false;
            int mode = inputAction.locateMode();
            return mode == LOCATE_TEXT || mode == LOCATE_DESC || mode == LOCATE_ID;
        }
    }

    // 内容与追加针脚仅在文本输入模式下显示
    private static class ContentShowablePin extends ShowAblePin {
        public ContentShowablePin(PinBase value, int titleId, boolean out, boolean dynamic, boolean hide) {
            super(value, titleId, out, dynamic, hide);
        }

        @Override
        public boolean showAble(Task context) {
            if (!(context.getAction(getOwnerId()) instanceof EditTextInputAction action)) return false;
            PinSingleSelect select = action.modePin.getValue();
            return select == null || select.getIndex() == 0;
        }
    }

    private static class EnterShowablePin extends ShowAblePin {
        public EnterShowablePin(PinBase value, int titleId, boolean out, boolean dynamic, boolean hide) {
            super(value, titleId, out, dynamic, hide);
        }

        @Override
        public boolean showAble(Task context) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        }
    }
}
