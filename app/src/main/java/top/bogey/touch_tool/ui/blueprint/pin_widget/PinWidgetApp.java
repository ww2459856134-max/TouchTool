package top.bogey.touch_tool.ui.blueprint.pin_widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.PinSubType;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_application.PinApplication;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinApplications;
import top.bogey.touch_tool.databinding.PinWidgetAppBinding;
import top.bogey.touch_tool.databinding.PinWidgetAppItemBinding;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.ui.blueprint.selecter.select_app.SelectAppDialog;

@SuppressLint("ViewConstructor")
public class PinWidgetApp extends PinWidget<PinApplication> {
    private final PinWidgetAppBinding binding;
    private final PinApplications applications;

    public PinWidgetApp(@NonNull Context context, ActionCard card, PinView pinView, PinApplication pinBase, boolean custom) {
        super(context, card, pinView, pinBase, custom);
        binding = PinWidgetAppBinding.inflate(LayoutInflater.from(context), this, true);

        if (pinBase.getSubType() == PinSubType.NORMAL) applications = new PinApplications(PinSubType.SINGLE_APP_WITH_ACTIVITY);
        else applications = new PinApplications(pinBase.getSubType());

        if (pinBase.getPackageName() != null && !pinBase.getPackageName().isEmpty()) applications.add(pinBase);
        init();
    }

    @Override
    protected void initBase() {
        binding.selectAppButton.setOnClickListener(v -> new SelectAppDialog(getContext(), applications, result -> {
            refreshApps();
            pinView.getPin().notifyValueUpdated(card.getTask());
        }).show());
        refreshApps();
    }

    @Override
    protected void initCustom() {

    }

    private void refreshApps() {
        binding.iconBox.removeAllViews();

        if (applications == null || applications.isEmpty()) {
            pinBase.reset();
            return;
        }

        PinApplication app = (PinApplication) applications.get(0);
        pinBase.setPackageName(app.getPackageName());
        List<String> classes = app.getActivityClasses();
        if (classes == null || classes.isEmpty()) pinBase.setActivityClasses(null);
        else pinBase.setActivityClasses(new ArrayList<>(classes));

        if (pinBase.getPackageName() == null || pinBase.getPackageName().isEmpty()) return;

        PackageManager manager = getContext().getPackageManager();

        PinWidgetAppItemBinding itemBinding = PinWidgetAppItemBinding.inflate(LayoutInflater.from(getContext()), binding.iconBox, true);
        itemBinding.exclude.setVisibility(GONE);

        PackageInfo info = TaskInfoSummary.getInstance().getAppInfo(pinBase.getPackageName());
        if (info == null || info.applicationInfo == null) {
            itemBinding.icon.setImageResource(R.drawable.icon_help);
        } else {
            itemBinding.icon.setImageDrawable(info.applicationInfo.loadIcon(manager));
        }

        if (classes == null || classes.isEmpty()) {
            itemBinding.numberBox.setVisibility(GONE);
        } else {
            itemBinding.numberText.setText(String.valueOf(classes.size()));
        }
        itemBinding.getRoot().setOnClickListener(v -> {
            applications.clear();
            refreshApps();
        });
    }
}
