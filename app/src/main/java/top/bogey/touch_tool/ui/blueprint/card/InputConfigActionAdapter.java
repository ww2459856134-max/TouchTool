package top.bogey.touch_tool.ui.blueprint.card;

import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.ui.blueprint.pin.PinInputConfigCustomView;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;

public class InputConfigActionAdapter extends CustomActionCardAdapter {
    public InputConfigActionAdapter(ActionCard card) {
        super(card);
    }

    @Override
    public PinView addPin(Pin pin) {
        PinView pinView = new PinInputConfigCustomView(card.getContext(), card, pin);
        pinViews.add(pinView);
        notifyItemInserted(pinViews.size() - 1);
        return pinView;
    }

    @Override
    public void swap(int from, int to) {
        super.swap(from, to);
        ((InputConfigActionCard) card).swap(from, to);
    }
}
