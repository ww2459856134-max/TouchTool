package top.bogey.touch_tool.bean.pin.pin_objects.pin_string;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import top.bogey.touch_tool.bean.other.NodeInfo;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.utils.AppUtil;

public class PinNodePathTextString extends PinNodePathString {

    public PinNodePathTextString() {
        super(PinSubType.NODE_PATH_TEXT);
    }

    public PinNodePathTextString(String str) {
        super(PinSubType.NODE_PATH_TEXT, str);
    }

    public PinNodePathTextString(JsonObject jsonObject) {
        super(jsonObject);
    }

    @Override
    public NodeInfo findNode(List<NodeInfo> roots, boolean fullPath) {
        return null;
    }

    public List<NodeInfo> findNodes(List<NodeInfo> roots) {
        if (value == null || value.isEmpty()) return null;
        String[] paths = value.split("\n");
        if (paths.length == 0) return null;
        return findNodes(roots, paths, 0);
    }

    private List<NodeInfo> findNodes(List<NodeInfo> nodes, String[] paths, int index) {
        List<NodeInfo> result = new ArrayList<>();
        if (index >= paths.length) return result;
        Pattern pattern = AppUtil.getPattern(paths[index]);
        if (pattern == null) return result;
        for (NodeInfo nodeInfo : nodes) {
            if (pattern.matcher(nodeInfo.getPath()).find()) {
                if (index == paths.length - 1) result.add(nodeInfo);
                else result.addAll(findNodes(nodeInfo.getChildren(), paths, index + 1));
            }
        }
        return result;
    }
}
