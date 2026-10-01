package top.bogey.touch_tool.bean.other;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleNodeInfo {
    public String clazz;
    public String id;
    public int index = 1;

    protected SimpleNodeInfo() {
    }

    public SimpleNodeInfo(String path) {
        Pattern pattern = Pattern.compile("^([a-zA-Z0-9.]+)$");
        // 代表没有任何额外信息的节点
        if (pattern.matcher(path).find()) {
            clazz = path;
        } else {
            pattern = Pattern.compile("^(.+?)(\\[.+])$");
            Matcher matcher = pattern.matcher(path);
            if (matcher.find()) {
                clazz = matcher.group(1);
                String detail = matcher.group(2);
                if (detail == null) return;

                String[] strings = detail.split("\\[");
                for (String string : strings) {
                    if (string.isEmpty()) continue;
                    List<String> regexes = Arrays.asList("id=(.+)]", "(\\d+)]");
                    for (int i = 0; i < regexes.size(); i++) {
                        String regex = regexes.get(i);
                        pattern = Pattern.compile(regex);
                        matcher = pattern.matcher(string);
                        if (matcher.find()) {
                            switch (i) {
                                case 0 -> id = matcher.group(1);
                                case 1 -> index = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    public boolean matchRootNode(SimpleNodeInfo nodeInfo, boolean fullPath) {
        if (fullPath) return matchNodeClass(nodeInfo) && (index == 1 || matchNodeId(nodeInfo));
        else return matchNodeClass(nodeInfo);
    }

    public boolean matchNodeClass(SimpleNodeInfo nodeInfo) {
        return Objects.equals(clazz, nodeInfo.clazz);
    }

    public boolean matchNodeId(SimpleNodeInfo nodeInfo) {
        return Objects.equals(id, nodeInfo.id);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SimpleNodeInfo info = (SimpleNodeInfo) o;
        return index == info.index && Objects.equals(clazz, info.clazz) && Objects.equals(id, info.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, id, index);
    }
}
