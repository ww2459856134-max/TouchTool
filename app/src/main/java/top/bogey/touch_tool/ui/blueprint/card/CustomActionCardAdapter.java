package top.bogey.touch_tool.ui.blueprint.card;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.ui.blueprint.pin.PinBottomCustomView;
import top.bogey.touch_tool.ui.blueprint.pin.PinLeftCustomView;
import top.bogey.touch_tool.ui.blueprint.pin.PinRightCustomView;
import top.bogey.touch_tool.ui.blueprint.pin.PinTopCustomView;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.utils.ui.IDragAbleRecycleViewAdapter;

public class CustomActionCardAdapter extends RecyclerView.Adapter<CustomActionCardAdapter.ViewHolder> implements IDragAbleRecycleViewAdapter {
    protected final List<PinView> pinViews = new ArrayList<>();
    protected final ActionCard card;

    public CustomActionCardAdapter(ActionCard card) {
        this.card = card;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout frameLayout = new FrameLayout(parent.getContext());
        frameLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new ViewHolder(frameLayout);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.refresh(pinViews.get(position));
    }

    @Override
    public int getItemCount() {
        return pinViews.size();
    }

    public PinView addPin(Pin pin) {
        PinView pinView;
        if (pin.isOut()) {
            if (pin.isVertical()) {
                pinView = new PinBottomCustomView(card.getContext(), card, pin);
            } else {
                pinView = new PinRightCustomView(card.getContext(), card, pin);
            }
        } else {
            if (pin.isVertical()) {
                pinView = new PinTopCustomView(card.getContext(), card, pin);
            } else {
                pinView = new PinLeftCustomView(card.getContext(), card, pin);
            }
        }
        pinViews.add(pinView);
        notifyItemInserted(pinViews.size() - 1);
        return pinView;
    }

    public void removePin(Pin pin) {
        for (int i = 0; i < pinViews.size(); i++) {
            PinView pinView = pinViews.get(i);
            if (pinView.getPin().equals(pin)) {
                pinViews.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    @Override
    public void swap(int from, int to) {
        Pin fromPin = pinViews.get(from).getPin();
        Pin toPin = pinViews.get(to).getPin();
        pinViews.add(to, pinViews.remove(from));

        List<Pin> pins = card.getAction().getPins();
        int i = pins.indexOf(fromPin);
        int j = pins.indexOf(toPin);
        pins.add(j, pins.remove(i));

        notifyItemMoved(from, to);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout layout;

        public ViewHolder(@NonNull FrameLayout itemView) {
            super(itemView);
            layout = itemView;
        }

        public void refresh(PinView pinView) {
            layout.removeAllViews();
            ViewGroup parent = (ViewGroup) pinView.getParent();
            if (parent != null) parent.removeView(pinView);
            layout.addView(pinView);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) pinView.getLayoutParams();
            params.gravity = pinView.getPin().isOut() ? Gravity.END : Gravity.START;
            pinView.setLayoutParams(params);

            ViewGroup.LayoutParams layoutParams = layout.getLayoutParams();
            layoutParams.width = pinView.getPin().isVertical() ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
            layout.setLayoutParams(layoutParams);
        }
    }
}
