package top.bogey.touch_tool.bean.other.run;

import java.util.ArrayList;
import java.util.List;

// 单个动作最近一次运行结果（内存即焚，不持久化）
public class ActionRunResult {
    // 0=成功 1=未达成(走了else分支) 2=异常
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_UNACHIEVED = 1;
    public static final int STATUS_ERROR = 2;

    public final int status;
    public final long duration;
    public final long timestamp;
    // 输出针脚快照：[针脚名, 针脚值文本]
    public final List<String[]> outputs = new ArrayList<>();
    // 失败时自动保存的屏幕截图路径（可能为空）
    public String screenshotPath;

    public ActionRunResult(int status, long duration) {
        this.status = status;
        this.duration = duration;
        this.timestamp = System.currentTimeMillis();
    }
}
