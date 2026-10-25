package top.bogey.touch_tool.bean.action.normal;

import android.graphics.Point;

import com.google.gson.JsonObject;

import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.CalculateAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.service.TaskRunnable;
import top.bogey.touch_tool.utils.GsonUtil;

public class ShowTextAction extends CalculateAction {
    private Point size = new Point();

    public ShowTextAction() {
        super(ActionType.SHOW_TEXT);
    }

    public ShowTextAction(JsonObject jsonObject) {
        super(jsonObject);
        size = GsonUtil.getAsObject(jsonObject, "size", Point.class, new Point());
    }

    @Override
    public void calculate(TaskRunnable runnable, Pin pin) {
    }

    public Point getSize() {
        return size;
    }

    public void setSize(Point size) {
        this.size = size;
    }
}
