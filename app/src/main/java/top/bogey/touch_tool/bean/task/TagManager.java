package top.bogey.touch_tool.bean.task;

import java.util.ArrayList;
import java.util.List;

public class TagManager implements ITagManager {
    private final List<String> tags = new ArrayList<>();

    @Override
    public void addTag(String tag) {
        if (tags.contains(tag)) return;
        tags.add(tag);
    }

    @Override
    public void removeTag(String tag) {
        tags.remove(tag);
    }

    @Override
    public List<String> getTags() {
        return tags;
    }

    @Override
    public void setTags(List<String> tags) {
        this.tags.clear();
        this.tags.addAll(tags);
    }

    @Override
    public String getTagString() {
        if (tags.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String tag : tags) {
            builder.append(tag).append(",");
        }
        return builder.substring(0, builder.length() - 1);
    }
}
