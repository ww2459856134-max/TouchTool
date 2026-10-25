package top.bogey.touch_tool.bean.base;

import com.google.gson.JsonObject;

import java.util.UUID;

import top.bogey.touch_tool.utils.GsonUtil;

public abstract class Identity implements Copyable {
    protected String uid;
    protected String id;

    protected String title;
    protected String description;

    public Identity() {
        uid = UUID.randomUUID().toString();
        id = UUID.randomUUID().toString();
    }

    public Identity(JsonObject jsonObject) {
        uid = GsonUtil.getAsString(jsonObject, "uid", UUID.randomUUID().toString());
        id = GsonUtil.getAsString(jsonObject, "id", UUID.randomUUID().toString());
        title = GsonUtil.getAsString(jsonObject, "title", "");
        description = GsonUtil.getAsString(jsonObject, "description", "");
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFullDescription() {
        if (getTitle() == null || getTitle().isEmpty()) return getDescription();
        if (getDescription() == null || getDescription().isEmpty()) return getTitle();
        return getTitle() + "-" + getDescription();
    }

    public String getValidDescription() {
        if (getDescription() == null || getDescription().isEmpty()) return getTitle();
        return getDescription();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Identity identity = (Identity) o;
        return getUid().equals(identity.getUid()) && getId().equals(identity.getId());
    }

    @Override
    public int hashCode() {
        int result = getUid().hashCode();
        result = 31 * result + getId().hashCode();
        return result;
    }
}
