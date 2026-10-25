package top.bogey.touch_tool.bean.pin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_execute.PinExecute;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;

// 把针脚值转成一段能看懂的短文本（值预览、运行值记录共用）
public class PinDescribe {

    // 返回 null 表示没有可展示的值（纯执行线等）
    public static String describe(PinBase value) {
        if (value == null) return null;
        if (value instanceof PinExecute) return null;
        if (value instanceof PinImage) return "（图片模板，无文本值）";
        String s = value.toString();
        if (s == null || s.isEmpty()) {
            try {
                JsonElement element = JsonParser.parseString(new com.google.gson.Gson().toJson(value));
                if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    JsonElement v = object.get("value");
                    if (v != null && !v.isJsonNull() && v.isJsonPrimitive()) s = v.getAsString();
                }
            } catch (Exception ignored) {
            }
        }
        if (s == null || s.isEmpty()) return null;
        s = s.replace("\n", " ");
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
