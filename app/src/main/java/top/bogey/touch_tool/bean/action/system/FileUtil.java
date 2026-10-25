package top.bogey.touch_tool.bean.action.system;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;

// 文件工具类，提供通用的路径校验与目录遍历方法，全部使用 java.io 保证全版本兼容
public class FileUtil {

    // 校验路径合法性：非空
    public static boolean checkPath(String path) {
        return path != null && !path.trim().isEmpty();
    }

    // 受保护的目录：这些路径本身及其上层目录（如 /storage 挡住 /storage/emulated/0）都禁止删除等危险操作
    private static final String[] FORBIDDEN_PATHS = {
            "/",
            "/sdcard",
            "/storage",
            "/storage/emulated",
            "/storage/emulated/0",
            "/storage/emulated/legacy",
            "/system",
            "/data",
            "/vendor",
            "/proc",
            "/sys",
            "/dev",
            "/etc",
            "/storage/emulated/0/Android",
            "/storage/emulated/0/DCIM",
            "/storage/emulated/0/Pictures",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Documents",
            "/storage/emulated/0/Movies",
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Alarms",
            "/storage/emulated/0/Ringtones",
            "/storage/emulated/0/Notifications",
            "/storage/emulated/0/tencent",
            "/storage/emulated/0/Android/data",
            "/storage/emulated/0/Android/obb"
    };

    // 校验路径安全性：解析软链接后（canonical），不能是受保护目录本身，也不能是受保护目录的上级
    // 例如 /sdcard 是指向 /storage/emulated/0 的软链接，getAbsoluteFile() 不解析它，必须用 getCanonicalFile()
    public static boolean checkPathSafe(String path) {
        if (!checkPath(path)) return false;
        String canonical;
        try {
            canonical = new File(path.trim()).getCanonicalPath();
        } catch (IOException e) {
            return false;
        }
        if (canonical.isEmpty()) return false;
        String withSlash = canonical.endsWith("/") ? canonical : canonical + "/";
        for (String forbidden : FORBIDDEN_PATHS) {
            if (canonical.equals(forbidden)) return false;
            // canonical 是 forbidden 的上级目录（如 /storage 是 /storage/emulated/0 的上级）同样拒绝
            if (forbidden.startsWith(withSlash)) return false;
        }
        return true;
    }

    // 校验源和目标的关系：同一文件，或目标在源目录内部，都会导致复制/移动出错（清空自身或无限递归）
    // 返回 true 表示关系安全
    public static boolean checkPathRelation(File from, File to) {
        if (from == null || to == null) return false;
        String fromPath;
        String toPath;
        try {
            fromPath = from.getCanonicalPath();
            toPath = to.getCanonicalPath();
        } catch (IOException e) {
            return false;
        }
        if (fromPath.equals(toPath)) return false;
        // 目标在源目录内部
        String fromWithSlash = fromPath.endsWith("/") ? fromPath : fromPath + "/";
        return !toPath.startsWith(fromWithSlash);
    }

    // 递归删除文件或目录，返回删除的条目数量，失败返回 -1
    public static long deleteRecursive(File file) {
        long count = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return -1;
            for (File child : children) {
                long result = deleteRecursive(child);
                if (result < 0) return -1;
                count += result;
            }
        }
        if (file.delete()) return count + 1;
        return -1;
    }

    // 列出目录下的一层条目，返回路径列表
    public static List<String> listDir(File dir) {
        File[] children = dir.listFiles();
        List<String> list = new ArrayList<>();
        if (children == null) return list;
        List<File> files = new ArrayList<>(Arrays.asList(children));
        Collections.sort(files);
        for (File item : files) {
            list.add(item.getAbsolutePath());
        }
        return list;
    }

    // 递归复制文件或目录，自动创建缺失的父目录
    public static boolean copyRecursive(File from, File to) throws java.io.IOException {
        if (from.isDirectory()) {
            File[] children = from.listFiles();
            if (!to.exists() && !to.mkdirs()) return false;
            if (children == null) return false;
            boolean success = true;
            for (File child : children) {
                if (!copyRecursive(child, new File(to, child.getName()))) success = false;
            }
            return success;
        }
        File parent = to.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
        try (java.io.FileInputStream input = new java.io.FileInputStream(from); java.io.FileOutputStream output = new java.io.FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        }
        return true;
    }

    // 把 PinList 的值设置为字符串列表
    public static void setStringListValue(Pin pin, List<String> list) {
        PinList pinList = pin.getValue(PinList.class);
        pinList.clear();
        for (String item : list) {
            pinList.add(new PinString(item));
        }
    }
}
