package top.bogey.touch_tool.ui.blueprint.selecter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import top.bogey.touch_tool.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// 文件路径选择对话框：基于真实文件系统浏览（应用已持有所有文件访问权限），返回绝对路径
public class SelectFileDialog {

    public interface FileSelectCallback {
        void onSelected(File file);
    }

    private final Context context;
    private final FileSelectCallback callback;
    // 目录模式：点击“选择此目录”返回当前浏览的目录
    private final boolean directory;
    private File currentDir;
    private androidx.appcompat.app.AlertDialog dialog;
    private PathAdapter adapter;
    private TextView pathView;

    public SelectFileDialog(Context context, FileSelectCallback callback) {
        this(context, false, callback);
    }

    public SelectFileDialog(Context context, boolean directory, FileSelectCallback callback) {
        this.context = context;
        this.callback = callback;
        this.directory = directory;
        this.currentDir = Environment.getExternalStorageDirectory();
    }

    public void show() {
        // 没有"所有文件访问"权限时系统会过滤普通文件（只见文件夹不见文件），引导授权
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(context, R.string.select_file_storage_permission, Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                context.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        }

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (context.getResources().getDisplayMetrics().density * 12);
        layout.setPadding(pad, pad, pad, 0);

        pathView = new TextView(context);
        pathView.setTextSize(12);
        pathView.setSingleLine(true);
        pathView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        pathView.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(pathView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new PathAdapter();
        recyclerView.setAdapter(adapter);
        layout.addView(recyclerView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.select_file_dialog_title)
                .setView(layout)
                .setNegativeButton(android.R.string.cancel, null);
        if (directory) {
            builder.setPositiveButton(R.string.select_file_dialog_select_dir, (d, w) -> {
                if (callback != null) callback.onSelected(currentDir);
            });
        }
        dialog = builder.show();
        refresh();
    }

    private void refresh() {
        pathView.setText(currentDir.getAbsolutePath());
        List<File> items = new ArrayList<>();
        File parent = currentDir.getParentFile();
        if (parent != null && parent.canRead()) items.add(parent);
        File[] files = currentDir.listFiles();
        if (files == null) {
            // 目录不可读（权限受限）：清空列表避免误以为没有文件
            adapter.setItems(items);
            return;
        }
        List<File> dirs = new ArrayList<>();
        List<File> fileList = new ArrayList<>();
        for (File file : files) {
            if (file.isHidden()) continue;
            if (file.isDirectory()) dirs.add(file);
            else fileList.add(file);
        }
        Collections.sort(dirs);
        Collections.sort(fileList);
        items.addAll(dirs);
        items.addAll(fileList);
        adapter.setItems(items);
    }

    private class PathAdapter extends RecyclerView.Adapter<PathViewHolder> {
        private final List<File> items = new ArrayList<>();

        public void setItems(List<File> files) {
            items.clear();
            items.addAll(files);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PathViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView view = new TextView(context);
            int pad = (int) (context.getResources().getDisplayMetrics().density * 10);
            view.setPadding(pad, pad, pad, pad);
            view.setTextSize(15);
            view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new PathViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PathViewHolder holder, int position) {
            File file = items.get(position);
            File parent = currentDir.getParentFile();
            boolean isParent = parent != null && file.getAbsolutePath().equals(parent.getAbsolutePath());
            boolean isDir = file.isDirectory() || isParent;
            String name = isParent ? "↩️ .." : (file.isDirectory() ? "📁 " : "📄 ") + file.getName();
            holder.textView.setText(name);
            holder.textView.setOnClickListener(v -> {
                if (isDir) {
                    currentDir = file;
                    refresh();
                } else if (callback != null) {
                    callback.onSelected(file);
                    dialog.dismiss();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class PathViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        public PathViewHolder(@NonNull TextView itemView) {
            super(itemView);
            textView = itemView;
        }
    }
}
