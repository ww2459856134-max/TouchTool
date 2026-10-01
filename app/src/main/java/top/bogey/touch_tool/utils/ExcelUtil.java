package top.bogey.touch_tool.utils;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

// 轻量 XLSX 读写引擎：零第三方依赖
// 读取支持：字符串(共享/内联)、数字、公式结果单元格
// 写入说明：全部按文本(inlineStr)写入；保存为单工作表，复杂样式与公式不保留
public class ExcelUtil {

    // 读取指定工作表的全部单元格，键为 "行:列"（从 1 开始），值为单元格文本
    public static Map<String, String> readCells(String path, int sheetIndex) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> sharedStrings = readSharedStrings(path);
        String sheetXml = readSheetXml(path, sheetIndex);
        if (sheetXml == null) return result;

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
                        if (target.startsWith("/")) return readZipEntry(path, target.substring(1));
                        return readZipEntry(path, "xl/" + target);
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

    // 写单元格：文件不存在时自动创建；已存在时读取后整体重写为单工作表
    public static void writeCell(String path, int sheetIndex, int row, int col, String value) throws Exception {
        Map<String, String> cells = new LinkedHashMap<>();
        try {
            cells = readCells(path, sheetIndex);
        } catch (Exception ignored) {
            // 文件不存在或无法读取时创建新表
        }
        if (value != null && !value.isEmpty()) {
            cells.put(row + ":" + col, value);
        } else {
            cells.remove(row + ":" + col);
        }
        writeWorkbook(path, cells);
    }

    // 追加一行：内容按分隔符拆分为多个单元格
    public static void appendRow(String path, int sheetIndex, String line, String colSep) throws Exception {
        Map<String, String> cells = new LinkedHashMap<>();
        try {
            cells = readCells(path, sheetIndex);
        } catch (Exception ignored) {
        }
        int maxRow = 0;
        for (String key : cells.keySet()) {
            maxRow = Math.max(maxRow, Integer.parseInt(key.split(":")[0]));
        }
        int row = maxRow + 1;
        String[] values = line.split(colSep, -1);
        for (int i = 0; i < values.length; i++) {
            if (!values[i].isEmpty()) cells.put(row + ":" + (i + 1), values[i]);
        }
        writeWorkbook(path, cells);
    }

    // 将单元格表写为全新的 xlsx 文件（单工作表，内联字符串）
    private static void writeWorkbook(String path, Map<String, String> cells) throws Exception {
        // 统计行列范围
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
                String value = cells.get(r + ":" + c);
                if (value == null) value = "";
                sheet.append("<c r=\"").append(colName(c)).append(r).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escapeXml(value)).append("</t></is></c>");
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");

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
            putEntry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        }
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

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
