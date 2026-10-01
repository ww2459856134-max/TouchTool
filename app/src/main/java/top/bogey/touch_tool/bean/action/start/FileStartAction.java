package top.bogey.touch_tool.bean.action.start;

import android.os.FileObserver;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleLineString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.service.TaskRunnable;

// 文件变更触发器：监控指定目录的文件新建、修改、删除、移动事件
public class FileStartAction extends StartAction {
    private final transient Pin pathPin = new NotLinkAblePin(new PinSingleLineString(), R.string.file_start_action_path);
    private final transient Pin eventPin = new NotLinkAblePin(new PinSingleSelect(R.array.file_event_type), R.string.file_start_action_event);
    private final transient Pin changedPathPin = new Pin(new PinString(), R.string.file_start_action_changed_path, true);
    private final transient Pin changedEventPin = new Pin(new PinString(), R.string.file_start_action_changed_event, true);

    public FileStartAction() {
        super(ActionType.FILE_START);
        addPins(pathPin, eventPin, changedPathPin, changedEventPin);
    }

    public FileStartAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(pathPin, eventPin, changedPathPin, changedEventPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        TaskInfoSummary.FileEventInfo info = TaskInfoSummary.getInstance().getFileEventInfo();
        if (info == null) return;

        changedPathPin.setValue(new PinString(info.path()));
        changedEventPin.setValue(new PinString(info.event()));

        executeNext(runnable, executePin);
    }

    @Override
    public boolean ready() {
        TaskInfoSummary.FileEventInfo info = TaskInfoSummary.getInstance().getFileEventInfo();
        if (info == null) return false;
        String path = getPath();
        if (path == null || path.isEmpty()) return false;
        // 事件必须发生在监控目录内
        if (!info.path().startsWith(path)) return false;

        String event = getEvent();
        // 选项为“任意事件”时不限定事件类型
        if (event == null || event.isEmpty()) return true;
        return event.equals(info.event());
    }

    public String getPath() {
        return pathPin.getValue(PinSingleLineString.class).getValue();
    }

    // 获取监控事件的掩码，选项顺序：任意事件/新建/修改/删除/移动
    public int getMask() {
        PinSingleSelect select = eventPin.getValue(PinSingleSelect.class);
        return switch (select.getIndex()) {
            case 1 -> FileObserver.CREATE;
            case 2 -> FileObserver.CLOSE_WRITE;
            case 3 -> FileObserver.DELETE;
            case 4 -> FileObserver.MOVED_TO | FileObserver.MOVED_FROM;
            default -> FileObserver.CREATE | FileObserver.CLOSE_WRITE | FileObserver.DELETE | FileObserver.MOVED_TO | FileObserver.MOVED_FROM;
        };
    }

    public String getEvent() {
        PinSingleSelect select = eventPin.getValue(PinSingleSelect.class);
        // 索引 0 是“任意事件”
        if (select.getIndex() <= 0) return "";
        return select.getValue();
    }

    // 把 FileObserver 的事件掩码转换为可读的事件名
    public static String eventName(int event) {
        event &= FileObserver.ALL_EVENTS;
        if ((event & FileObserver.CREATE) != 0) return MainApplication.getInstance().getString(R.string.file_event_create);
        if ((event & FileObserver.CLOSE_WRITE) != 0) return MainApplication.getInstance().getString(R.string.file_event_modify);
        if ((event & FileObserver.DELETE) != 0) return MainApplication.getInstance().getString(R.string.file_event_delete);
        if ((event & (FileObserver.MOVED_TO | FileObserver.MOVED_FROM)) != 0) return MainApplication.getInstance().getString(R.string.file_event_move);
        return "";
    }
}
