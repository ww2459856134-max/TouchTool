package top.bogey.touch_tool.bean.action.parent;

import androidx.annotation.NonNull;

import java.util.List;

import top.bogey.touch_tool.bean.pin.Pin;

public interface DynamicTypePinsAction {
    @NonNull
    List<Pin> getDynamicTypePins();

    @NonNull
    List<Pin> getDynamicKeyTypePins();

    @NonNull
    List<Pin> getDynamicValueTypePins();
}
