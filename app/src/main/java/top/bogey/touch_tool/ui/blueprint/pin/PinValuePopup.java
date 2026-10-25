package top.bogey.touch_tool.ui.blueprint.pin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.PinDescribe;
import top.bogey.touch_tool.bean.pin.PinRunValueRecorder;
import top.bogey.touch_tool.utils.DisplayUtil;

// 针脚值预览气泡：长按针脚弹出，显示默认值和上次运行值
public class PinValuePopup {

    private static String buildText(Pin pin) {
        String desc = PinDescribe.describe(pin.getValue());
        if (desc == null) return null;
        if (desc.isEmpty()) desc = "（无默认值，运行时由连线来源提供）";

        StringBuilder builder = new StringBuilder(pin.getTitle());
        builder.append("\n").append(pin.isLinked() ? "已连线：运行时取连线来源的值\n当前默认值：" + desc : desc);
        String lastRun = pin.getLastRunValue();
        PinRunValueRecorder.Record record = PinRunValueRecorder.get(pin.getOwnerId(), pin);
        if (record != null) {
            String time = android.text.format.DateFormat.format("HH:mm:ss", record.time).toString();
            builder.append("\n上次运行值（").append(time).append("）：").append(record.value);
        } else if (lastRun != null) {
            String time = android.text.format.DateFormat.format("HH:mm:ss", pin.getLastRunTime()).toString();
            builder.append("\n上次运行值（").append(time).append("）：").append(lastRun);
        }
        return builder.toString();
    }

    private static PopupWindow build(Context context, Pin pin) {
        String text = buildText(pin);
        if (text == null) return null;

        MaterialCardView cardView = new MaterialCardView(context);
        cardView.setRadius(DisplayUtil.dp2px(context, 8));
        cardView.setCardBackgroundColor(0xF2121212);
        cardView.setElevation(DisplayUtil.dp2px(context, 6));

        TextView textView = new TextView(context);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(12);
        int padding = (int) DisplayUtil.dp2px(context, 10);
        textView.setPadding(padding, padding, padding, padding);
        textView.setText(text);
        textView.setTextIsSelectable(true);
        cardView.addView(textView);

        PopupWindow popup = new PopupWindow(cardView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return popup;
    }

    // 从锚点视图下方弹出（普通视图场景）
    public static void showAsDropDown(View anchor, Pin pin) {
        Context context = anchor.getContext();
        PopupWindow popup = build(context, pin);
        if (popup == null) return;
        popup.showAsDropDown(anchor);
    }

    // 在指定父视图坐标系位置弹出（画布自绘触摸场景）
    public static void showAt(Context context, Pin pin, View parent, float x, float y) {
        PopupWindow popup = build(context, pin);
        if (popup == null) return;
        popup.showAtLocation(parent, android.view.Gravity.NO_GRAVITY, (int) x, (int) y);
    }
}
