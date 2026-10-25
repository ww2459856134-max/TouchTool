package top.bogey.touch_tool.ui.blueprint.pin_widget;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.IOException;
import java.io.InputStream;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.databinding.PinWidgetImageBinding;
import top.bogey.touch_tool.ui.MainActivity;
import top.bogey.touch_tool.ui.blueprint.card.ActionCard;
import top.bogey.touch_tool.ui.blueprint.picker.ImagePickerPreview;
import top.bogey.touch_tool.ui.blueprint.pin.PinView;
import top.bogey.touch_tool.ui.blueprint.selecter.select_icon.SelectIconDialog;
import top.bogey.touch_tool.utils.DisplayUtil;

@SuppressLint("ViewConstructor")
public class PinWidgetImage extends PinWidget<PinImage> {
    private final PinWidgetImageBinding binding;

    public PinWidgetImage(@NonNull Context context, ActionCard card, PinView pinView, PinImage pinBase, boolean custom) {
        super(context, card, pinView, pinBase, custom);
        binding = PinWidgetImageBinding.inflate(LayoutInflater.from(context), this, true);
        init();
    }

    @Override
    protected void initBase() {
        binding.image.setImageBitmap(pinBase.getImage());
        binding.image.setOnClickListener(v -> {
            Bitmap bitmap = pinBase.getImage();
            if (bitmap == null) return;

            ShapeableImageView view = new ShapeableImageView(getContext());
            view.setImageBitmap(bitmap);

            new MaterialAlertDialogBuilder(getContext())
                    .setPositiveButton(R.string.enter, null)
                    .setNegativeButton(R.string.clear, (dialog, which) -> {
                        pinBase.setImage(null);
                        pinView.getPin().notifyValueUpdated(card.getTask());
                        binding.image.setImageBitmap(null);
                    })
                    .setView(view)
                    .show();

            int margin = (int) DisplayUtil.dp2px(getContext(), 16);
            DisplayUtil.setViewMargin(view, margin, margin, margin, margin);
        });

        switch (pinBase.getSubType()) {
            case NORMAL -> binding.pickButton.setOnClickListener(v -> new ImagePickerPreview(getContext(), image -> {
                pinBase.setImage(image);
                pinView.getPin().notifyValueUpdated(card.getTask());
                binding.image.setImageBitmap(image);
            }, pinBase.getImage()).show());
            case WITH_ICON -> binding.pickButton.setOnClickListener(v -> new SelectIconDialog(getContext(), result -> {
                pinBase.setImage(result);
                pinView.getPin().notifyValueUpdated(card.getTask());
                binding.image.setImageBitmap(result);
            }).show());
            case FILE_CONTENT -> binding.pickButton.setOnClickListener(v -> {
                MainActivity activity = MainApplication.getInstance().getActivity();
                activity.launcherPickMedia((code, intent) -> {
                    if (code == Activity.RESULT_OK) {
                        Bitmap bitmap = null;
                        Uri uri = intent.getData();
                        if (uri != null) {
                            try (InputStream inputStream = activity.getContentResolver().openInputStream(uri)) {
                                bitmap = BitmapFactory.decodeStream(inputStream);
                            } catch (IOException ignored) {
                            }
                        }
                        pinBase.setImage(bitmap);
                        pinView.getPin().notifyValueUpdated(card.getTask());
                        binding.image.setImageBitmap(bitmap);
                    }
                }, ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE);
            });
        }
    }

    @Override
    protected void initCustom() {

    }
}
