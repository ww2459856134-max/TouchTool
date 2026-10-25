package top.bogey.touch_tool.utils;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

// 轻量 XLSX 读写引擎：零第三方依赖
// 读取支持：字符串(共享/内联)、数字、公式结果单元格
// 写入策略：保留原文件的其他条目（其他工作表、样式、共享字符串），仅重建目标工作表
// 数字写入：按数字类型写入的单元格不带文本标记，Excel 中可直接求和
// CSV：提供独立的表格读写（自动处理 BOM，UTF-8 优先、GBK 回退）
public class ExcelUtil {

    // ========== XLSX 读取 ==========

    // 读取指定工作表的全部单元格，键为 "行:列"（从 1 开始），值为单元格文本
    public static Map<String, String> readCells(String path, int sheetIndex) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> sharedStrings = readSharedStrings(path);
        String sheetXml = readSheetXml(path, sheetIndex);
        if (sheetXml == null) throw new Exception("工作表序号超出范围（共 " + readSheetNames(path).size() + " 张）");

        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new java.io.StringReader(sheetXml));
        int row = 0;
        int col = 0;
        int type = parser.getEventType();
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG) {
                switch (parser.getName()) {
                    case "row" -> {
                        String r = parser.getAttributeValue(null, "r");
                        row = r == null ? row + 1 : Integer.parseInt(r.split(":")[0]);
                        col = 0;
                    }
                    case "c" -> {
                        String r = parser.getAttributeValue(null, "r");
                        String t = parser.getAttributeValue(null, "t");
                        if (r != null) {
                            int[] pos = parseCellRef(r);
                            row = pos[0];
                            col = pos[1];
                        } else {
                            col++;
                        }
                        // 读取单元格值
                        String value = readCellValue(parser, t, sharedStrings);
                        if (value != null && !value.isEmpty()) {
                            result.put(row + ":" + col, value);
                        }
                    }
                }
            }
            type = parser.next();
        }
        return result;
    }

    // 列出全部工作表名称
    public static List<String> readSheetNames(String path) throws Exception {
        List<String> names = new ArrayList<>();
        String workbook = readZipEntry(path, "xl/workbook.xml");
        if (workbook == null) return names;
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new java.io.StringReader(workbook));
        int type = parser.getEventType();
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && "sheet".equals(parser.getName())) {
                String name = parser.getAttributeValue(null, "name");
                names.add(name == null ? "Sheet" : name);
            }
            type = parser.next();
        }
        return names;
    }

    // 解析单元格的值，遇到 c 结束标签为止
    private static String readCellValue(XmlPullParser parser, String t, List<String> sharedStrings) throws Exception {
        if ("inlineStr".equals(t)) {
            // 内联字符串：<is><t>...</t></is>
            StringBuilder builder = new StringBuilder();
            int type = parser.next();
            while (type != XmlPullParser.END_TAG || !"c".equals(parser.getName())) {
                if (type == XmlPullParser.START_TAG && "t".equals(parser.getName())) {
                    builder.append(parser.nextText());
                }
                type = parser.next();
            }
            return builder.toString();
        }

        String raw = null;
        int type = parser.next();
        while (type != XmlPullParser.END_TAG || !"c".equals(parser.getName())) {
            if (type == XmlPullParser.START_TAG && "v".equals(parser.getName())) {
                raw = parser.nextText();
            }
            type = parser.next();
        }
        if (raw == null) return "";
        // 共享字符串表引用
        if ("s".equals(t)) {
            int index = Integer.parseInt(raw.trim());
            return index < sharedStrings.size() ? sharedStrings.get(index) : "";
        }
        return raw;
    }

    // 读取共享字符串表
    private static List<String> readSharedStrings(String path) throws Exception {
        List<String> strings = new ArrayList<>();
        String xml = readZipEntry(path, "xl/sharedStrings.xml");
        if (xml == null) return strings;

        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new java.io.StringReader(xml));
        StringBuilder builder = new StringBuilder();
        boolean inSi = false;
        int type = parser.getEventType();
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG) {
                if ("si".equals(parser.getName())) {
                    inSi = true;
                    builder.setLength(0);
                } else if (inSi && "t".equals(parser.getName())) {
                    builder.append(parser.nextText());
                }
            } else if (type == XmlPullParser.END_TAG && "si".equals(parser.getName())) {
                strings.add(builder.toString());
                inSi = false;
            }
            type = parser.next();
        }
        return strings;
    }

    // 通过 workbook.xml 与关系文件定位第 N 个工作表（sheetIndex 从 1 开始）
    private static String readSheetXml(String path, int sheetIndex) throws Exception {
        String entry = locateSheetEntry(path, sheetIndex);
        return entry == null ? null : readZipEntry(path, entry);
    }

    // 定位第 N 个工作表在压缩包内的条目名
    private static String locateSheetEntry(String path, int sheetIndex) throws Exception {
        String workbook = readZipEntry(path, "xl/workbook.xml");
        String rels = readZipEntry(path, "xl/_rels/workbook.xml.rels");
        if (workbook == null || rels == null) return null;

        // 收集工作表声明的 r:id
        List<String> sheetIds = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new java.io.StringReader(workbook));
        int type = parser.getEventType();
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && "sheet".equals(parser.getName())) {
                String id = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                sheetIds.add(id);
            }
            type = parser.next();
        }
        if (sheetIndex < 1 || sheetIndex > sheetIds.size()) return null;
        String targetId = sheetIds.get(sheetIndex - 1);

        // 通过关系文件找到目标路径
        parser = Xml.newPullParser();
        parser.setInput(new java.io.StringReader(rels));
        type = parser.getEventType();
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && "Relationship".equals(parser.getName())) {
                if (targetId.equals(parser.getAttributeValue(null, "Id"))) {
                    String target = parser.getAttributeValue(null, "Target");
                    if (target != null) {
                        if (target.startsWith("/")) return target.substring(1);
                        return "xl/" + target;
                    }
                }
            }
            type = parser.next();
        }
        return null;
    }

    // 读取压缩包内指定条目
    private static String readZipEntry(String path, String entryName) throws Exception {
        try (InputStream in = new FileInputStream(path); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zip.read(buffer)) > 0) out.write(buffer, 0, len);
                    return out.toString("UTF-8");
                }
            }
        }
        return null;
    }

    // 读单元格（行、列从 1 开始），不存在返回空字符串
    public static String readCell(String path, int sheetIndex, int row, int col) throws Exception {
        return readCells(path, sheetIndex).getOrDefault(row + ":" + col, "");
    }

    // 读全表为文本：列用列分隔符、行用换行分隔
    public static String readAll(String path, int sheetIndex, String colSep) throws Exception {
        Map<String, String> cells = readCells(path, sheetIndex);
        int maxRow = 0;
        int maxCol = 0;
        for (String key : cells.keySet()) {
            String[] parts = key.split(":");
            maxRow = Math.max(maxRow, Integer.parseInt(parts[0]));
            maxCol = Math.max(maxCol, Integer.parseInt(parts[1]));
        }
        StringBuilder builder = new StringBuilder();
        for (int r = 1; r <= maxRow; r++) {
            if (r > 1) builder.append('\n');
            for (int c = 1; c <= maxCol; c++) {
                if (c > 1) builder.append(colSep);
                builder.append(cells.getOrDefault(r + ":" + c, ""));
            }
        }
        return builder.toString();
    }

    // ========== XLSX 写入 ==========

    // A1 记法转行列：B3 → {3, 2}
    public static int[] parseA1(String ref) {
        return parseCellRef(ref.trim());
    }

    // 外科手术式写公式：内容如 SUM(A1:A9)，Excel/WPS 打开时计算；返回 false 表示表结构无法安全修改
    public static boolean surgicalWriteFormula(String path, int sheetIndex, int row, int col, String formula) {
        try {
            String sheetXml = readSheetXml(path, sheetIndex);
            if (sheetXml == null || !sheetXml.contains("<sheetData")) return false;
            String text = formula.trim();
            if (text.startsWith("=")) text = text.substring(1);
            if (text.isEmpty()) return false;
            String ref = colName(col) + row;
            String styleAttr = findCellStyleAttr(sheetXml, ref);
            String s = styleAttr == null ? "" : " " + styleAttr;
            String newCell = "<c r=\"" + ref + "\"" + s + "><f>" + escapeXml(text) + "</f></c>";
            String updated = upsertCell(sheetXml, row, ref, newCell);
            if (updated == null) return false;
            String entry = locateSheetEntry(path, sheetIndex);
            if (entry == null) return false;
            rewriteZip(path, Map.of(entry, updated));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 外科手术式删除整行：摘除该行节点，其他内容原样保留
    public static boolean surgicalDeleteRow(String path, int sheetIndex, int row) {
        try {
            String sheetXml = readSheetXml(path, sheetIndex);
            if (sheetXml == null || !sheetXml.contains("<sheetData")) return false;
            java.util.regex.Matcher rowStart = java.util.regex.Pattern
                    .compile("<row\\b[^>]*\\br=\"" + row + "\"[^>]*?(/?)>").matcher(sheetXml);
            if (!rowStart.find()) return false; // 行不存在，视为无需修改
            boolean selfClosing = !rowStart.group(1).isEmpty();
            int start = rowStart.start();
            int end;
            if (selfClosing) {
                end = rowStart.end();
            } else {
                int close = sheetXml.indexOf("</row>", rowStart.end());
                if (close < 0) return false;
                end = close + "</row>".length();
            }
            String updated = sheetXml.substring(0, start) + sheetXml.substring(end);
            String entry = locateSheetEntry(path, sheetIndex);
            if (entry == null) return false;
            rewriteZip(path, Map.of(entry, updated));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 外科手术式删除整列：从每一行摘除该列的格子，其他内容原样保留
    public static boolean surgicalDeleteColumn(String path, int sheetIndex, int col) {
        try {
            String sheetXml = readSheetXml(path, sheetIndex);
            if (sheetXml == null || !sheetXml.contains("<sheetData")) return false;
            java.util.regex.Matcher cells = java.util.regex.Pattern
                    .compile("<c\\b[^>]*?\\br=\"([A-Z]+)(\\d+)\"[^>]*?(/?)>").matcher(sheetXml);
            List<int[]> spans = new ArrayList<>();
            while (cells.find()) {
                if (colNameToIndex(cells.group(1)) != col) continue;
                boolean selfClosing = !cells.group(3).isEmpty();
                int start = cells.start();
                int end;
                if (selfClosing) {
                    end = cells.end();
                } else {
                    int close = sheetXml.indexOf("</c>", cells.end());
                    if (close < 0) return false;
                    end = close + "</c>".length();
                }
                spans.add(new int[]{start, end});
            }
            if (spans.isEmpty()) return true; // 该列本来就没有内容
            String updated = sheetXml;
            for (int i = spans.size() - 1; i >= 0; i--) {
                updated = updated.substring(0, spans.get(i)[0]) + updated.substring(spans.get(i)[1]);
            }
            String entry = locateSheetEntry(path, sheetIndex);
            if (entry == null) return false;
            rewriteZip(path, Map.of(entry, updated));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 外科手术式写单元格：只替换/插入/删除目标格子，表内其他内容（样式、合并、公式）原样保留
    // 返回 false 表示该表结构无法安全修改（调用方回退整表重建）
    public static boolean surgicalWriteCell(String path, int sheetIndex, int row, int col, String value, boolean numeric) {
        try {
            String sheetXml = readSheetXml(path, sheetIndex);
            if (sheetXml == null || !sheetXml.contains("<sheetData")) return false;
            String ref = colName(col) + row;
            // 原格子若有样式引用（s 属性），写值时保留
            String styleAttr = findCellStyleAttr(sheetXml, ref);
            String newCell = value.isEmpty() ? null : buildCellXml(ref, value, numeric, styleAttr);
            String updated = upsertCell(sheetXml, row, ref, newCell);
            if (updated == null) return false;
            String entry = locateSheetEntry(path, sheetIndex);
            if (entry == null) return false;
            rewriteZip(path, Map.of(entry, updated));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 外科手术式追加一行：在表格末尾插入新行，其他行原样保留；纯数字自动按数字类型
    public static boolean surgicalAppendRow(String path, int sheetIndex, String line, String colSep) {
        try {
            String sheetXml = readSheetXml(path, sheetIndex);
            if (sheetXml == null || !sheetXml.contains("<sheetData")) return false;
            int maxRow = 0;
            java.util.regex.Matcher rowMatcher = java.util.regex.Pattern.compile("<row\\b[^>]*\\br=\"(\\d+)\"").matcher(sheetXml);
            while (rowMatcher.find()) maxRow = Math.max(maxRow, Integer.parseInt(rowMatcher.group(1)));
            int newRow = maxRow + 1;

            StringBuilder rowXml = new StringBuilder();
            String[] values = line.split(colSep, -1);
            for (int i = 0; i < values.length; i++) {
                String value = values[i].trim();
                if (value.isEmpty()) continue;
                String ref = colName(i + 1) + newRow;
                boolean numeric = value.matches("-?\\d+(\\.\\d+)?");
                rowXml.append(buildCellXml(ref, value, numeric, null));
            }
            if (rowXml.length() == 0) return true; // 空行无需修改

            String insert = "<row r=\"" + newRow + "\">" + rowXml + "</row>";
            int sheetDataEnd = sheetXml.indexOf("</sheetData>");
            if (sheetDataEnd < 0) return false;
            String updated = sheetXml.substring(0, sheetDataEnd) + insert + sheetXml.substring(sheetDataEnd);
            String entry = locateSheetEntry(path, sheetIndex);
            if (entry == null) return false;
            rewriteZip(path, Map.of(entry, updated));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 生成单元格 XML；numeric 且内容是数字时按数字类型（无 t 属性），否则内联字符串
    private static String buildCellXml(String ref, String value, boolean numeric, String styleAttr) {
        String s = styleAttr == null ? "" : " " + styleAttr;
        if (numeric && value.matches("-?\\d+(\\.\\d+)?")) {
            return "<c r=\"" + ref + "\"" + s + "><v>" + escapeXml(value) + "</v></c>";
        }
        return "<c r=\"" + ref + "\"" + s + " t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                + escapeXml(value) + "</t></is></c>";
    }

    // 在 sheet XML 中定位某格子的样式引用（s="N"），没有返回 null
    private static String findCellStyleAttr(String sheetXml, String ref) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<c\\b[^>]*\\br=\"" + Pattern_quote(ref) + "\"[^>]*>").matcher(sheetXml);
        if (!matcher.find()) return null;
        java.util.regex.Matcher s = java.util.regex.Pattern.compile("\\bs=\"\\d+\"").matcher(matcher.group());
        return s.find() ? s.group() : null;
    }

    private static String Pattern_quote(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) sb.append(java.util.regex.Pattern.quote(String.valueOf(c)));
        return sb.toString();
    }

    // 在表中插入/替换/删除单元格，行和格子都按原表顺序插入；结构超出预期返回 null
    private static String upsertCell(String sheetXml, int row, String ref, String newCell) {
        // 1. 定位目标行（普通行或自闭合行）
        java.util.regex.Matcher rowStart = java.util.regex.Pattern
                .compile("<row\\b[^>]*\\br=\"" + row + "\"[^>]*?(/?)>").matcher(sheetXml);
        if (!rowStart.find()) {
            // 行不存在：按行号顺序插入新行
            String newRow = "<row r=\"" + row + "\">" + (newCell == null ? "" : newCell) + "</row>";
            java.util.regex.Matcher rows = java.util.regex.Pattern.compile("<row\\b[^>]*\\br=\"(\\d+)\"").matcher(sheetXml);
            int insertAt = -1;
            while (rows.find()) {
                if (Integer.parseInt(rows.group(1)) > row) {
                    insertAt = rows.start();
                    break;
                }
            }
            if (insertAt < 0) {
                int end = sheetXml.indexOf("</sheetData>");
                if (end < 0) return null;
                insertAt = end;
            }
            return sheetXml.substring(0, insertAt) + newRow + sheetXml.substring(insertAt);
        }
        boolean selfClosing = !rowStart.group(1).isEmpty();
        int rowStartPos = rowStart.start();
        int rowEndPos;
        if (selfClosing) {
            rowEndPos = rowStart.end();
        } else {
            int close = sheetXml.indexOf("</row>", rowStart.end());
            if (close < 0) return null;
            rowEndPos = close + "</row>".length();
        }

        String beforeRow = sheetXml.substring(0, rowStartPos);
        String afterRow = sheetXml.substring(rowEndPos);
        // 行头：自闭合行去掉 "/>" 换成 ">"
        String rowHead = selfClosing
                ? rowStart.group().substring(0, rowStart.group().length() - 2) + ">"
                : rowStart.group();
        String rowBody = selfClosing ? "" : sheetXml.substring(rowStart.end(), rowEndPos - "</row>".length());

        String updatedBody;
        if (selfClosing) {
            // 自闭合行（空行）：展开并放入新格子
            if (newCell == null) return sheetXml; // 删除不存在的格子 = 无操作
            updatedBody = newCell;
        } else {
            // 在行内按列顺序插入/替换/删除
            String result = upsertCellInRow(rowBody, ref, newCell);
            if (result == null) return null;
            updatedBody = result;
        }
        return beforeRow + rowHead + updatedBody + "</row>" + afterRow;
    }

    // 在一行内容中按列顺序插入/替换/删除单元格
    private static String upsertCellInRow(String rowBody, String ref, String newCell) {
        int targetCol = parseCellRef(ref)[1];
        java.util.regex.Matcher cells = java.util.regex.Pattern.compile("<c\\b[^>]*?\\br=\"([A-Z]+)(\\d+)\"[^>]*?(/?)>").matcher(rowBody);
        int replaceStart = -1;
        int replaceEnd = -1;
        int insertAt = -1;
        while (cells.find()) {
            int cellCol = colNameToIndex(cells.group(1));
            int cellRow = Integer.parseInt(cells.group(2));
            if (cellRow != parseCellRef(ref)[0]) continue;
            boolean selfClosing = !cells.group(3).isEmpty();
            int start = cells.start();
            int end;
            if (selfClosing) {
                end = cells.end();
            } else {
                int close = rowBody.indexOf("</c>", cells.end());
                if (close < 0) return null;
                end = close + "</c>".length();
            }
            if (cellCol == targetCol) {
                replaceStart = start;
                replaceEnd = end;
                break;
            }
            if (cellCol > targetCol && insertAt < 0) insertAt = start;
        }
        if (replaceStart >= 0) {
            if (newCell == null) {
                // 删除格子
                return rowBody.substring(0, replaceStart) + rowBody.substring(replaceEnd);
            }
            return rowBody.substring(0, replaceStart) + newCell + rowBody.substring(replaceEnd);
        }
        if (newCell == null) return rowBody; // 删除不存在的格子 = 无操作
        if (insertAt < 0) return rowBody + newCell;
        return rowBody.substring(0, insertAt) + newCell + rowBody.substring(insertAt);
    }

    private static int colNameToIndex(String name) {
        int col = 0;
        for (int i = 0; i < name.length(); i++) {
            col = col * 26 + (Character.toUpperCase(name.charAt(i)) - 'A' + 1);
        }
        return col;
    }

    // 写单元格：优先外科手术，失败回退整表重建；文件不存在时自动创建
    // numeric 为 true 且内容是数字时按数字类型写入
    public static void writeCell(String path, int sheetIndex, int row, int col, String value, boolean numeric) throws Exception {
        File original = new File(path);
        if (original.exists() && original.length() > 0) {
            if (surgicalWriteCell(path, sheetIndex, row, col, value, numeric)) return;
        }
        Map<String, String> cells = new LinkedHashMap<>();
        Set<String> numericCells = new HashSet<>();
        try {
            loadWorkbook(path, sheetIndex, cells, numericCells);
        } catch (Exception ignored) {
            // 文件不存在或无法读取时创建新表
        }
        String key = row + ":" + col;
        if (value != null && !value.isEmpty()) {
            cells.put(key, value);
            if (numeric) numericCells.add(key);
            else numericCells.remove(key);
        } else {
            // 值为空 = 删除该单元格
            cells.remove(key);
            numericCells.remove(key);
        }
        writeWorkbook(path, cells, numericCells, sheetIndex);
    }

    // 追加一行：优先外科手术，失败回退整表重建；纯数字内容自动按数字类型写入
    public static void appendRow(String path, int sheetIndex, String line, String colSep) throws Exception {
        File original = new File(path);
        if (original.exists() && original.length() > 0) {
            if (surgicalAppendRow(path, sheetIndex, line, colSep)) return;
        }
        Map<String, String> cells = new LinkedHashMap<>();
        Set<String> numericCells = new HashSet<>();
        try {
            loadWorkbook(path, sheetIndex, cells, numericCells);
        } catch (Exception ignored) {
        }
        int maxRow = 0;
        for (String key : cells.keySet()) {
            maxRow = Math.max(maxRow, Integer.parseInt(key.split(":")[0]));
        }
        int row = maxRow + 1;
        String[] values = line.split(colSep, -1);
        for (int i = 0; i < values.length; i++) {
            String value = values[i].trim();
            if (value.isEmpty()) continue;
            String key = row + ":" + (i + 1);
            cells.put(key, value);
            if (value.matches("-?\\d+(\\.\\d+)?")) numericCells.add(key);
        }
        writeWorkbook(path, cells, numericCells, sheetIndex);
    }

    // 读取已有工作表的单元格与数字单元格集合
    private static void loadWorkbook(String path, int sheetIndex, Map<String, String> cells, Set<String> numericCells) throws Exception {
        Map<String, String> read = readCells(path, sheetIndex);
        // 识别原数字单元格：sheet XML 中无 t 属性的 c 元素，重写时保持数字类型
        String sheetXml = readSheetXml(path, sheetIndex);
        cells.putAll(read);
        if (sheetXml != null) numericCells.addAll(readNumericCellRefs(sheetXml));
    }

    // 从工作表 XML 中提取无 t 属性（即数字类型）的单元格引用
    private static Set<String> readNumericCellRefs(String sheetXml) throws Exception {
        Set<String> refs = new HashSet<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new java.io.StringReader(sheetXml));
        int type = parser.getEventType();
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && "c".equals(parser.getName())) {
                String r = parser.getAttributeValue(null, "r");
                String t = parser.getAttributeValue(null, "t");
                if (r != null && (t == null || t.isEmpty())) {
                    int[] pos = parseCellRef(r);
                    refs.add(pos[0] + ":" + pos[1]);
                }
            }
            type = parser.next();
        }
        return refs;
    }

    // 将单元格表写为 xlsx 文件：原文件存在时保留其他条目，仅替换目标工作表
    private static void writeWorkbook(String path, Map<String, String> cells, Set<String> numericCells, int sheetIndex) throws Exception {
        String targetEntry = null;
        File original = new File(path);
        if (original.exists() && original.length() > 0) {
            try {
                targetEntry = locateSheetEntry(path, sheetIndex);
            } catch (Exception ignored) {
            }
        }

        if (targetEntry != null) {
            // 保留模式：复制原包全部条目，仅重建目标工作表内容
            Map<String, String> replace = Map.of(targetEntry, buildSheetXml(cells, numericCells));
            rewriteZip(path, replace);
        } else {
            // 新建模式：生成最小可用的单工作表文件
            createFreshWorkbook(path, cells, numericCells);
        }
    }

    // 生成工作表 XML
    private static String buildSheetXml(Map<String, String> cells, Set<String> numericCells) {
        int maxRow = 0;
        int maxCol = 0;
        for (String key : cells.keySet()) {
            String[] parts = key.split(":");
            maxRow = Math.max(maxRow, Integer.parseInt(parts[0]));
            maxCol = Math.max(maxCol, Integer.parseInt(parts[1]));
        }

        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 1; r <= maxRow; r++) {
            sheet.append("<row r=\"").append(r).append("\">");
            for (int c = 1; c <= maxCol; c++) {
                String key = r + ":" + c;
                String value = cells.get(key);
                if (value == null) value = "";
                String ref = colName(c) + r;
                if (numericCells.contains(key) && value.matches("-?\\d+(\\.\\d+)?")) {
                    // 数字类型：无 t 属性，Excel 中可直接参与计算
                    sheet.append("<c r=\"").append(ref).append("\"><v>").append(escapeXml(value)).append("</v></c>");
                } else {
                    sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escapeXml(value)).append("</t></is></c>");
                }
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");
        return sheet.toString();
    }

    // 重写压缩包：复制原条目，replace 中的条目用新内容替换
    private static void rewriteZip(String path, Map<String, String> replace) throws Exception {
        File temp = new File(path + ".tmp");
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(path));
             ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(temp))) {
            byte[] buffer = new byte[8192];
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (replace.containsKey(name)) {
                    putEntry(zip, name, replace.get(name));
                    continue;
                }
                zip.putNextEntry(new ZipEntry(name));
                if (!entry.isDirectory()) {
                    int len;
                    while ((len = in.read(buffer)) > 0) zip.write(buffer, 0, len);
                }
                zip.closeEntry();
            }
        }
        File file = new File(path);
        if (!file.delete() || !temp.renameTo(file)) {
            throw new IOException("无法覆盖原文件");
        }
    }

    // 新建最小可用的 xlsx
    private static void createFreshWorkbook(String path, Map<String, String> cells, Set<String> numericCells) throws Exception {
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>";

        String rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";

        String workbook = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";

        String workbookRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";

        String styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                + "<borders count=\"1\"><border/></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
                + "</styleSheet>";

        try (OutputStream out = new FileOutputStream(path); ZipOutputStream zip = new ZipOutputStream(out)) {
            putEntry(zip, "[Content_Types].xml", contentTypes);
            putEntry(zip, "_rels/.rels", rootRels);
            putEntry(zip, "xl/workbook.xml", workbook);
            putEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels);
            putEntry(zip, "xl/styles.xml", styles);
            putEntry(zip, "xl/worksheets/sheet1.xml", buildSheetXml(cells, numericCells));
        }
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // ========== CSV ==========

    // 读 CSV 为表格（外层行、内层列），支持引号包裹与转义
    public static List<List<String>> readCsvGrid(String path, String sep) throws Exception {
        String text = readTextSmart(path);
        List<List<String>> grid = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.length() == 0) {
                inQuotes = true;
            } else if (text.startsWith(sep, i)) {
                row.add(cell.toString());
                cell.setLength(0);
                i += sep.length() - 1;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                grid.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        row.add(cell.toString());
        grid.add(row);
        return grid;
    }

    // 写 CSV 表格，需要引号的值自动加引号
    public static void writeCsvGrid(String path, List<List<String>> grid, String sep) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            for (List<String> row : grid) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) line.append(sep);
                    String value = row.get(i) == null ? "" : row.get(i);
                    if (value.contains(sep) || value.contains("\"") || value.contains("\n")) {
                        line.append('"').append(value.replace("\"", "\"\"")).append('"');
                    } else {
                        line.append(value);
                    }
                }
                writer.write(line.toString());
                writer.write("\n");
            }
        }
    }

    // 智能读文本：UTF-8 优先，出现替换字符时回退 GBK；剥离 BOM
    public static String readTextSmart(String path) throws Exception {
        byte[] bytes;
        try (FileInputStream in = new FileInputStream(path)) {
            bytes = in.readAllBytes();
        }
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        String text = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0) {
            text = new String(bytes, offset, bytes.length - offset, "GBK");
        }
        return text;
    }

    // ========== 通用工具 ==========

    // 列序号转字母：1=A, 26=Z, 27=AA
    private static String colName(int col) {
        StringBuilder builder = new StringBuilder();
        while (col > 0) {
            col--;
            builder.insert(0, (char) ('A' + col % 26));
            col /= 26;
        }
        return builder.toString();
    }

    // 单元格引用转行列：B3 → {3, 2}
    private static int[] parseCellRef(String ref) {
        int row = 0;
        int col = 0;
        int i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) {
            col = col * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1);
            i++;
        }
        while (i < ref.length() && Character.isDigit(ref.charAt(i))) {
            row = row * 10 + (ref.charAt(i) - '0');
            i++;
        }
        return new int[]{row, col};
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
