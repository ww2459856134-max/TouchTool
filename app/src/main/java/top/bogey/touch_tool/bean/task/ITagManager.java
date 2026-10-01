package top.bogey.touch_tool.bean.task;

import java.util.List;

public interface ITagManager {
    void addTag(String tag);

    void removeTag(String tag);

    List<String> getTags();

    void setTags(List<String> tags);

    String getTagString();
}
