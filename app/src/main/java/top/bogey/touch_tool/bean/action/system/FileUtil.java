package top.bogey.touch_tool.bean.action.system;

import java.io.File;
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

    // 校验路径安全性：不能是文件系统根目录（如 /、/sdcard），防止误删
    public static boolean checkPathSafe(String path) {
        if (!checkPath(path)) return false;
        File file = new File(path.trim());
        File absolute = file.getAbsoluteFile();
        // 根目录的父目录是它自己
        return absolute.getParentFile() != null;
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
