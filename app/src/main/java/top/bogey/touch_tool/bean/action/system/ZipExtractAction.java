package top.bogey.touch_tool.bean.action.system;

import com.google.gson.JsonObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.PinBoolean;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinFilePathString;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.pin.special_pin.ShowAblePin;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.TaskRunnable;

// ZIP 压缩/解压/预览三合一动作：模式针脚切换
// 前三个针脚（源路径、目标、结果）与旧版两个动作的位置一致，旧任务按位置同步不受影响
public class ZipExtractAction extends ExecuteAction {
    // 模式索引：0 解压（新动作默认），1 压缩，2 查看包内清单
    public static final int MODE_EXTRACT = 0;
    public static final int MODE_CREATE = 1;
    public static final int MODE_PREVIEW = 2;

    private final transient Pin sourcePin = new Pin(new PinFilePathString(), R.string.file_action_source_path);
    private final transient Pin targetPin = new Pin(new PinFilePathString(), R.string.file_action_target_path);
    private final transient Pin resultPin = new Pin(new PinBoolean(), R.string.file_action_success, true);
    // 模式针脚追加在末尾，旧任务按位置同步不受影响
    private final transient Pin modePin = new Pin(new PinSingleSelect(R.array.zip_action_mode, MODE_EXTRACT), R.string.zip_action_mode);
    // 覆盖已存在的文件：默认关（跳过同名文件）
    private final transient Pin overwritePin = new Pin(new PinBoolean(false), R.string.zip_action_overwrite);
    // 最大解压大小（MB）：防止 zip bomb 写满存储；0 为不限
    private final transient Pin maxSizePin = new Pin(new PinInteger(1024), R.string.zip_action_max_size);
    // 只解压匹配的文件：通配模式，逗号分隔，如 *.jpg, photo*.png；空为全部
    private final transient Pin filterPin = new FilterShowablePin(new PinString(), R.string.zip_action_filter);
    // 压缩时排除的文件：通配模式，逗号分隔；空为不排除
    private final transient Pin excludePin = new ExcludeShowablePin(new PinString(), R.string.zip_action_exclude);
    // 进度（0-100）
    private final transient Pin progressPin = new Pin(new PinInteger(0), R.string.zip_action_progress, true);
    // 文件清单：解压模式为实际解出的文件，预览模式为包内条目"名称 (大小)"
    private final transient Pin listPin = new Pin(new PinList(new PinString()), R.string.zip_action_list, true);

    public ZipExtractAction() {
        super(ActionType.ZIP_EXTRACT);
        addPins(sourcePin, targetPin, resultPin, modePin, overwritePin, maxSizePin, filterPin, excludePin, progressPin, listPin);
    }

    public ZipExtractAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(sourcePin, targetPin, resultPin, modePin, overwritePin, maxSizePin, filterPin, excludePin, progressPin, listPin);
    }

    // 读取当前模式
    public int mode() {
        PinSingleSelect mode = modePin.getValue(PinSingleSelect.class);
        return mode != null ? mode.getIndex() : MODE_EXTRACT;
    }

    // 设置模式（供旧类型子类在反序列化后固定模式）
    protected void setMode(int index) {
        modePin.getValue(PinSingleSelect.class).setIndex(index);
    }

    @Override
    public void execute(TaskRunnable runnable, Pin pin) {
        String source = getPinValue(runnable, sourcePin).toString();
        String target = getPinValue(runnable, targetPin).toString();
        resultPin.getValue(PinBoolean.class).setValue(false);
        progressPin.getValue(PinInteger.class).setValue(0);
        listPin.getValue(PinList.class).clear();
        if (FileUtil.checkPath(source) && FileUtil.checkPath(target)) {
            File zipFile = new File(source.trim());
            switch (mode()) {
                case MODE_CREATE -> doCreate(runnable, zipFile, new File(target.trim()));
                case MODE_PREVIEW -> doPreview(runnable, zipFile);
                default -> doExtract(runnable, zipFile, new File(target.trim()));
            }
        }
        executeNext(runnable, outPin);
    }

    // 解析逗号分隔的通配模式列表为正则；文本为空返回 null（不过滤）
    // 通配规则：* 任意字符，? 单个字符；匹配相对路径或文件名任一即可
    private List<Pattern> parsePatterns(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        List<Pattern> patterns = new ArrayList<>();
        for (String part : text.split("[,，;；]+")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String regex = p.chars()
                    .collect(StringBuilder::new, (sb, c) -> {
                        if (c == '*') sb.append(".*");
                        else if (c == '?') sb.append('.');
                        else sb.append(Pattern.quote(String.valueOf((char) c)));
                    }, StringBuilder::append).toString();
            patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }
        return patterns.isEmpty() ? null : patterns;
    }

    // 相对路径或文件名命中任一模式即算匹配
    private boolean matchAny(List<Pattern> patterns, String relativePath) {
        if (patterns == null) return true;
        String name = relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf('/') + 1) : relativePath;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(relativePath).matches() || pattern.matcher(name).matches()) return true;
        }
        return false;
    }

    // 解压 ZIP 到目录：带覆盖开关、大小上限、文件名过滤、进度与文件清单
    private void doExtract(TaskRunnable runnable, File zipFile, File targetDir) {
        if (!zipFile.isFile() || !(targetDir.isDirectory() || targetDir.mkdirs())) return;
        PinBoolean overwrite = getPinValue(runnable, overwritePin);
        PinInteger maxSize = getPinValue(runnable, maxSizePin);
        List<Pattern> filters = parsePatterns(getPinValue(runnable, filterPin).toString());
        long maxBytes = Math.max(0, maxSize.intValue()) * 1024L * 1024L;

        try {
            // 第一遍只数条目，用于进度百分比
            int total = 0;
            try (ZipInputStream countStream = new ZipInputStream(new FileInputStream(zipFile))) {
                while (countStream.getNextEntry() != null) total++;
            }
            if (total == 0) return;

            PinList list = listPin.getValue(PinList.class);
            boolean success = true;
            long written = 0;
            int processed = 0;
            try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(zipFile))) {
                byte[] buffer = new byte[8192];
                ZipEntry entry;
                while ((entry = zipStream.getNextEntry()) != null) {
                    if (runnable.isCurrentInterrupt()) return;
                    processed++;
                    progressPin.getValue(PinInteger.class).setValue(processed * 100 / total);

                    // 条目路径必须是目标目录的子路径，防止路径穿越（跳过恶意条目）
                    File file = new File(targetDir, entry.getName());
                    if (!file.getCanonicalPath().startsWith(targetDir.getCanonicalPath() + File.separator)) {
                        success = false;
                        continue;
                    }
                    if (entry.isDirectory()) {
                        file.mkdirs();
                        continue;
                    }
                    // 文件名过滤：不匹配的条目跳过
                    if (!matchAny(filters, entry.getName())) continue;
                    // 覆盖开关：目标存在且不允许覆盖时跳过
                    if (file.exists() && !(overwrite instanceof PinBoolean pinBoolean && pinBoolean.getValue())) continue;
                    // 大小上限：累计写入超过上限即中止，防 zip bomb
                    if (maxBytes > 0 && written > maxBytes) {
                        success = false;
                        break;
                    }
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        success = false;
                        continue;
                    }
                    try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
                        int length;
                        while ((length = zipStream.read(buffer)) > 0) {
                            written += length;
                            output.write(buffer, 0, length);
                        }
                    }
                    zipStream.closeEntry();
                    list.add(new PinString(entry.getName()));
                }
            }
            resultPin.getValue(PinBoolean.class).setValue(success);
        } catch (IOException ignored) {
        }
    }

    // 压缩文件或目录为 ZIP：带排除规则与进度
    private void doCreate(TaskRunnable runnable, File from, File to) {
        if (!from.exists()) return;
        File parent = to.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return;
        List<Pattern> excludes = parsePatterns(getPinValue(runnable, excludePin).toString());

        try {
            // 先数文件数，用于进度百分比
            int total = countFiles(from, excludes);
            PinList list = listPin.getValue(PinList.class);
            boolean success = true;
            try (ZipOutputStream zipStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(to)))) {
                // 目录压缩时条目以目录名开头，单文件时以文件名为根
                String root = from.isDirectory() ? from.getName() + "/" : from.getName();
                success = zipFile(runnable, zipStream, from, root, from.getName(), excludes, total, new int[]{0}, list);
            }
            if (total == 0) progressPin.getValue(PinInteger.class).setValue(100);
            resultPin.getValue(PinBoolean.class).setValue(success);
        } catch (IOException ignored) {
        }
    }

    // 统计将被压缩的文件数（已按排除规则过滤）
    private int countFiles(File file, List<Pattern> excludes) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return 0;
            int count = 0;
            for (File child : children) count += countFiles(child, excludes);
            return count;
        }
        return matchAny(excludes, file.getName()) ? 1 : 0;
    }

    // 递归写入 ZIP 条目；relativePath 为源内相对路径（用于排除匹配），processed 用数组做计数器
    private boolean zipFile(TaskRunnable runnable, ZipOutputStream zipStream, File file, String entryName,
                            String relativePath, List<Pattern> excludes, int total, int[] processed, PinList list) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            if (children.length == 0) {
                zipStream.putNextEntry(new ZipEntry(entryName));
                zipStream.closeEntry();
                return true;
            }
            boolean success = true;
            for (File child : children) {
                if (runnable.isCurrentInterrupt()) return false;
                String childRelative = relativePath.isEmpty() ? child.getName() : relativePath + "/" + child.getName();
                if (!zipFile(runnable, zipStream, child, entryName + child.getName() + (child.isDirectory() ? "/" : ""),
                        childRelative, excludes, total, processed, list)) {
                    success = false;
                }
            }
            return success;
        }
        // 排除规则：相对路径或文件名命中即跳过（不计进度）
        if (matchAny(excludes, relativePath)) {
            return true;
        }
        zipStream.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) > 0) {
                zipStream.write(buffer, 0, length);
            }
        }
        zipStream.closeEntry();
        processed[0]++;
        if (total > 0) progressPin.getValue(PinInteger.class).setValue(processed[0] * 100 / total);
        list.add(new PinString(relativePath));
        return true;
    }

    // 查看包内清单：不解压，输出"条目名 (大小)"
    private void doPreview(TaskRunnable runnable, File zipFile) {
        if (!zipFile.isFile()) return;
        try (ZipFile zip = new ZipFile(zipFile)) {
            PinList list = listPin.getValue(PinList.class);
            var entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                if (runnable.isCurrentInterrupt()) return;
                ZipEntry entry = entries.nextElement();
                long size = entry.getSize();
                list.add(new PinString(entry.getName() + (size >= 0 ? " (" + size + " 字节)" : "")));
                count++;
            }
            progressPin.getValue(PinInteger.class).setValue(100);
            resultPin.getValue(PinBoolean.class).setValue(count > 0);
        } catch (IOException ignored) {
        }
    }

    // 只在解压模式显示过滤参数
    private static class FilterShowablePin extends ShowAblePin {
        public FilterShowablePin(PinString value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            if (context.getAction(getOwnerId()) instanceof ZipExtractAction action) {
                return action.mode() == MODE_EXTRACT;
            }
            return true;
        }
    }

    // 只在压缩模式显示排除参数
    private static class ExcludeShowablePin extends ShowAblePin {
        public ExcludeShowablePin(PinString value, int titleId) {
            super(value, titleId);
        }

        @Override
        public boolean showAble(Task context) {
            if (context.getAction(getOwnerId()) instanceof ZipExtractAction action) {
                return action.mode() == MODE_CREATE;
            }
            return true;
        }
    }
}
