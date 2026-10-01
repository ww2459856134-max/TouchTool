package top.bogey.touch_tool.ui.blueprint.pin_widget;

import android.content.Context;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import top.bogey.touch_tool.bean.pin.pin_objects.PinBase;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;

public abstract class PinWidget<T extends PinBase> extends FrameLayout {
    protected final ActionCard card;
    protected final PinView pinView;
    protected final T pinBase;
    protected final boolean custom;

    public PinWidget(@NonNull Context context, ActionCard card, PinView pinView, T pinBase, boolean custom) {
        super(context);
        this.card = card;
        this.pinView = pinView;
        this.pinBase = pinBase;
        this.custom = custom;
    }

    protected void init() {
        initBase();
        if (custom) initCustom();
    }

    protected abstract void initBase();

    protected abstract void initCustom();
}
