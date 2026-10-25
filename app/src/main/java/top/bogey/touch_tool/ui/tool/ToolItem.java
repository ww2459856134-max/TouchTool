package top.bogey.touch_tool.ui.tool;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public record ToolItem(String id, @DrawableRes int icon, @StringRes int name) {
}
