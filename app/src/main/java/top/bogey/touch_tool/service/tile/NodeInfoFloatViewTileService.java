package top.bogey.touch_tool.service.tile;

import android.content.Context;

import top.bogey.touch_tool.ui.blueprint.picker.NodePickerPreview;

public class NodeInfoFloatViewTileService extends FloatViewTileService {
    @Override
    protected String getFloatViewName() {
        return NodePickerPreview.class.getName();
    }

    @Override
    protected void showFloatView(Context context) {
        new NodePickerPreview(context, null, null).show();
    }
}
