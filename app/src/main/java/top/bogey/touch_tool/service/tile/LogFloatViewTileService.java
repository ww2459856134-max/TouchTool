package top.bogey.touch_tool.service.tile;

import android.content.Context;

import top.bogey.touch_tool.ui.tool.log.LogFloatView;

public class LogFloatViewTileService extends FloatViewTileService {

    @Override
    protected String getFloatViewName() {
        return LogFloatView.class.getName();
    }

    @Override
    protected void showFloatView(Context context) {
        new LogFloatView(context).show();
    }
}
