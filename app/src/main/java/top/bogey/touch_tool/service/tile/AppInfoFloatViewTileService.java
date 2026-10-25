package top.bogey.touch_tool.service.tile;

import android.content.Context;

import top.bogey.touch_tool.ui.tool.app_info.AppInfoFloatView;

public class AppInfoFloatViewTileService extends FloatViewTileService {

    @Override
    protected String getFloatViewName() {
        return AppInfoFloatView.class.getName();
    }

    @Override
    protected void showFloatView(Context context) {
        new AppInfoFloatView(context).show();
    }

}
