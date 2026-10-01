package top.bogey.touch_tool.ui.blueprint.selecter.select_icon;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.databinding.DialogSelectIconPageItemBinding;
import top.bogey.touch_tool.utils.DisplayUtil;
import top.bogey.touch_tool.utils.callback.BitmapResultCallback;

public class SelectIconPageItemRecyclerViewAdapter extends RecyclerView.Adapter<SelectIconPageItemRecyclerViewAdapter.ViewHolder> {
    private final BitmapResultCallback callback;
    private List<Object> data = new ArrayList<>();

    public SelectIconPageItemRecyclerViewAdapter(BitmapResultCallback callback) {
        this.callback = callback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DialogSelectIconPageItemBinding binding = DialogSelectIconPageItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.refresh(data.get(position));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public void setData(List<Object> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final DialogSelectIconPageItemBinding binding;
        private final Context context;

        public ViewHolder(@NonNull DialogSelectIconPageItemBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
            context = binding.getRoot().getContext();

            binding.getRoot().setOnClickListener(v -> {
                int index = getBindingAdapterPosition();
                Object object = data.get(index);
                Drawable drawable = binding.icon.getDrawable();
                Bitmap bitmap;
                if (object instanceof Integer) {
                    int offset = (int) DisplayUtil.dp2px(context, 4);
                    bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth() + offset * 2, drawable.getIntrinsicHeight() + offset * 2, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.translate(offset, offset);
                    drawable.draw(canvas);
                } else {
                    bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    drawable.draw(canvas);
                }
                callback.onResult(bitmap);
            });
        }

        public void refresh(Object object) {
            if (object instanceof Integer id) {
                binding.icon.setImageResource(id);
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.icon.getLayoutParams();
                params.leftMargin = params.rightMargin = params.topMargin = params.bottomMargin = (int) DisplayUtil.dp2px(context, 8);
                binding.icon.setLayoutParams(params);
                binding.icon.setImageTintList(ColorStateList.valueOf(DisplayUtil.getAttrColor(context, com.google.android.material.R.attr.colorPrimaryVariant)));
            }

            if (object instanceof PackageInfo packageInfo) {
                if (packageInfo.applicationInfo != null) {
                    binding.icon.setImageDrawable(packageInfo.applicationInfo.loadIcon(context.getPackageManager()));
                }
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.icon.getLayoutParams();
                params.leftMargin = params.rightMargin = params.topMargin = params.bottomMargin = 0;
                binding.icon.setLayoutParams(params);
                binding.icon.setImageTintList(null);
            }
        }
    }
}
