package top.bogey.touch_tool.service.tile;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.start.QuickTileStartAction;
import top.bogey.touch_tool.bean.save.task.TaskSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.MainAccessibilityService;

// 任务磁贴基类：点击后执行绑定到对应磁贴编号的任务
public abstract class SlotTileService extends TileService {

    // 获取本磁贴服务的编号（1-4）
    protected abstract int getSlot();

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        MainAccessibilityService service = MainApplication.getInstance().getService();
        if (service == null || !service.isEnabled()) {
            Toast.makeText(this, R.string.quick_tile_start_service_off, Toast.LENGTH_SHORT).show();
            return;
        }

        List<Task> tasks = TaskSaver.getInstance().getTasks(QuickTileStartAction.class);
        boolean started = false;
        for (Task task : tasks) {
            for (Action action : task.getActions(QuickTileStartAction.class)) {
                QuickTileStartAction tileAction = (QuickTileStartAction) action;
                // 只执行绑定到本磁贴编号的任务
                if (tileAction.isEnable() && tileAction.getSlot() == getSlot()) {
                    service.runTask(task, tileAction);
                    started = true;
                }
            }
        }
        if (!started) {
            Toast.makeText(this, R.string.quick_tile_start_no_task, Toast.LENGTH_SHORT).show();
        }
        updateTile();
    }

    // 刷新磁贴状态，短暂高亮表示已执行
    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
