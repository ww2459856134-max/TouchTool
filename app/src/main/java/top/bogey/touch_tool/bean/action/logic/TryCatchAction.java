package top.bogey.touch_tool.bean.action.logic;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.service.TaskRunnable;

// 异常捕获：尝试线里任何动作抛出异常都会被接住，转走出错线，任务不再直接中断
// 手段就是把"尝试"分支的同线程执行链包在自己的 try-catch 里
public class TryCatchAction extends ExecuteAction {
    private final transient Pin tryPin = new Pin(new PinExecute(), R.string.try_catch_action_try, true);
    private final transient Pin catchPin = new Pin(new PinExecute(), R.string.try_catch_action_catch, true);
    private final transient Pin errorPin = new Pin(new PinString(), R.string.try_catch_action_error, true);

    public TryCatchAction() {
        super(ActionType.TRY_CATCH_LOGIC);
        addPins(tryPin, catchPin, errorPin);
    }

    public TryCatchAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(tryPin, catchPin, errorPin);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        errorPin.setValue(new PinString(""));
        try {
            executeNext(runnable, tryPin);
        } catch (Exception e) {
            // 用户主动停止任务时不拦截，保持原有的中断行为
            if (runnable.isInterrupt()) throw e;
            e.printStackTrace();
            String message = e.toString();
            if (e.getMessage() != null) message = e.getClass().getSimpleName() + ": " + e.getMessage();
            errorPin.setValue(new PinString(message));
            executeNext(runnable, catchPin);
        }
    }
}
