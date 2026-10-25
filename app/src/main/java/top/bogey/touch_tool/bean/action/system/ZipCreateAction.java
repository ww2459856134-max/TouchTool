package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

// 旧「压缩 ZIP」动作：已并入 ZipExtractAction（模式=压缩），保留本类只为旧任务反序列化
// 不再出现在新增动作菜单里；加载旧任务时模式自动固定为「压缩」
public class ZipCreateAction extends ZipExtractAction {

    public ZipCreateAction() {
        super();
        setMode(MODE_CREATE);
    }

    public ZipCreateAction(JsonObject jsonObject) {
        super(jsonObject);
        setMode(MODE_CREATE);
    }
}
