package top.bogey.touch_tool.bean.action.start;

import com.google.gson.JsonObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionCheckResult;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinDate;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinPeriodic;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinTime;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.NotLinkAblePin;
import top.bogey.touch_tool.bean.save.setting.SettingSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.AppUtil;

public class TimeStartAction extends StartAction {
    private final transient Pin showTimeLogPin = new NotLinkAblePin(new PinBoolean(true), R.string.time_start_action_show_time_log);
    private final transient Pin datePin = new Pin(new PinDate(), R.string.time_start_action_date);
    private final transient Pin timePin = new Pin(new PinTime(), R.string.time_start_action_time);
    private final transient Pin periodic = new Pin(new PinPeriodic(), R.string.time_start_action_periodic);
    // 多时间点：每天固定的 HH:mm 列表（如 08:00,12:30,21:00），留空不用
    private final transient Pin timesPin = new NotLinkAblePin(new PinString(), R.string.time_start_action_times);
    // 随机延迟：到点后随机等 0~N 分钟再执行，0 = 不延迟
    private final transient Pin randomPin = new Pin(new PinInteger(0), R.string.time_start_action_random);
    // 倒计时：从设置时刻起 X 分钟后执行一次，触发后自动清零，0 = 关闭
    private final transient Pin countdownPin = new Pin(new PinInteger(0), R.string.time_start_action_countdown);

    public TimeStartAction() {
        super(ActionType.TIME_START);
        // 新针脚必须追加在末尾，旧任务按位置同步
        addPins(showTimeLogPin, datePin, timePin, periodic, timesPin, randomPin, countdownPin);
    }

    public TimeStartAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(showTimeLogPin, datePin, timePin, periodic, timesPin, randomPin, countdownPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        super.execute(runnable, pin);
        executeNext(runnable, executePin);
    }

    public long getStartTime() {
        long date = datePin.getValue(PinDate.class).getValue();
        long time = timePin.getValue(PinTime.class).getValue();
        return AppUtil.mergeDateTime(date, time);
    }

    public long getPeriodic() {
        return periodic.getValue(PinPeriodic.class).getValue();
    }

    public boolean isShowTimeLog() {
        return showTimeLogPin.getValue(PinBoolean.class).getValue();
    }

    public long getRandomDelay() {
        return randomPin.getValue(PinInteger.class).getValue();
    }

    public long getCountdownMinutes() {
        return countdownPin.getValue(PinInteger.class).getValue();
    }

    // 倒计时触发后清零，保证只执行一次
    public void resetCountdown() {
        countdownPin.setValue(new PinInteger(0));
    }


    // 统一计算下一次触发时间，返回毫秒时间戳，不再需要排期返回 -1
    public long getNextStartTime(long current) {
        // 倒计时模式：从现在起 X 分钟后
        long countdown = getCountdownMinutes();
        if (countdown > 0) return current + countdown * 60 * 1000L;

        // 多时间点模式：每天固定的 HH:mm 列表，取最近的下一个
        String times = timesPin.getValue(PinString.class).getValue();
        if (times != null && !times.trim().isEmpty()) {
            long best = -1;
            for (String part : times.split("[,，]")) {
                String[] hm = part.trim().split(":");
                if (hm.length != 2) continue;
                try {
                    int hour = Integer.parseInt(hm[0].trim());
                    int minute = Integer.parseInt(hm[1].trim());
                    if (hour < 0 || hour > 23 || minute < 0 || minute > 59) continue;
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(current);
                    calendar.set(Calendar.HOUR_OF_DAY, hour);
                    calendar.set(Calendar.MINUTE, minute);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    // 今天的这个点已过，排到明天
                    if (calendar.getTimeInMillis() <= current) calendar.add(Calendar.DAY_OF_MONTH, 1);
                    if (best < 0 || calendar.getTimeInMillis() < best) best = calendar.getTimeInMillis();
                } catch (NumberFormatException ignored) {
                }
            }
            return best;
        }

        // 原逻辑：固定日期时间 + 可选间隔循环
        long next = getStartTime();
        long periodicValue = getPeriodic();
        if (periodicValue > 0) {
            long l = current - next;
            if (l > 0) {
                int loop = (int) Math.ceil(l * 1f / periodicValue);
                next = next + loop * periodicValue;
                // 如果算出的下个开始时间没有大于现在时间30s，应该是算错了，下个开始时间再加个间隔
                if (next - current < 30 * 1000) next += periodicValue;
            }
        }
        return next < current ? -1 : next;
    }

    @Override
    public void check(ActionCheckResult result, Task task) {
        super.check(result, task);
        if (!SettingSaver.PERMISSION_EXACT_ALARM.get()) {
            result.addResult(ActionCheckResult.ResultType.ERROR, R.string.check_need_exact_alarm_permission_error);
        }
    }
}
