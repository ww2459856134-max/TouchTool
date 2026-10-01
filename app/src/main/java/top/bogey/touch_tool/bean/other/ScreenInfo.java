package top.bogey.touch_tool.bean.other;

import android.graphics.Bitmap;

import java.util.List;

import top.bogey.touch_tool.service.MainAccessibilityService;

public class ScreenInfo {
    private Bitmap screenShot;
    private final List<NodeInfo> rootNodes;

    public ScreenInfo(MainAccessibilityService service) {
        screenShot = service.tryGetScreenShot();
        rootNodes = NodeInfo.getWindows();
    }

    public Bitmap getScreenShot() {
        return screenShot;
    }

    public void setScreenShot(Bitmap screenShot) {
        this.screenShot = screenShot;
    }

    public List<NodeInfo> getRootNodes() {
        return rootNodes;
    }
}
