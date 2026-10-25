package top.bogey.touch_tool.bean.save;

import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.bogey.touch_tool.bean.save.task.TaskSaver;
import top.bogey.touch_tool.bean.save.variable.VariableSaver;
import top.bogey.touch_tool.utils.GsonUtil;
import top.bogey.touch_tool.utils.ThreadUtil;

public class TagSaver {
    private static TagSaver instance;

    public static TagSaver getInstance() {
        synchronized (TagSaver.class) {
            if (instance == null) {
                instance = new TagSaver();
            }
        }
        return instance;
    }

    private static final String TAG_DB = "TAG_DB";

    private final MMKV mmkv = MMKV.mmkvWithID(TAG_DB, MMKV.SINGLE_PROCESS_MODE);
    private List<String> tags;
    private final Map<String, List<String>> taskOrder = new HashMap<>();

    private TagSaver() {
        String[] keys = mmkv.allKeys();
        if (keys == null) {
            tags = new ArrayList<>();
        } else {
            tags = GsonUtil.getAsObject(mmkv.decodeString(TAG_DB, "[]"), TypeToken.getParameterized(ArrayList.class, String.class).getType(), new ArrayList<>());
            for (String key : keys) {
                if (TAG_DB.equals(key)) continue;
                taskOrder.put(key, GsonUtil.getAsObject(mmkv.decodeString(key, "[]"), TypeToken.getParameterized(ArrayList.class, String.class).getType(), new ArrayList<>()));
            }
        }
    }

    public void addTag(String tag) {
        tags.add(tag);
        mmkv.encode(TAG_DB, GsonUtil.toJson(tags));
    }

    public synchronized void removeTag(String tag) {
        tags.remove(tag);
        mmkv.encode(TAG_DB, GsonUtil.toJson(tags));

        ThreadUtil.execute(() -> {
            TaskSaver.getInstance().removeTasksTag(tag);
            VariableSaver.getInstance().removeVariablesTag(tag);
        });
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
        mmkv.encode(TAG_DB, GsonUtil.toJson(tags));
    }

    public void setTaskOrder(String tag, List<String> order) {
        taskOrder.put(tag, order);
        mmkv.encode(tag, GsonUtil.toJson(order));
    }

    public List<String> getTaskOrder(String tag) {
        return taskOrder.get(tag);
    }
}
