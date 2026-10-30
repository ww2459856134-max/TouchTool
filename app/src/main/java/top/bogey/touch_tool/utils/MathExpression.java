package top.bogey.touch_tool.utils;

import androidx.annotation.Nullable;

// 数值表达式求值：支持 + - * / % 与括号，用于数值针脚插值后的运算（如 {单价}*{数量}）
// 整数无小数点返回 Long，否则返回 Double；任何解析失败返回 null，不抛异常
public final class MathExpression {

    private MathExpression() {
    }

    @Nullable
    public static Number evaluate(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty()) return null;
        try {
            return new Parser(s.replace("×", "*").replace("÷", "/")).parseExpression();
        } catch (Exception e) {
            return null;
        }
    }

    private static final class Parser {
        private final String s;
        private int pos;

        private Parser(String s) {
            this.s = s;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    char op = s.charAt(pos++);
                    double right = parseTerm();
                    value = op == '+' ? value + right : value - right;
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipSpaces();
                if (pos < s.length() && (s.charAt(pos) == '*' || s.charAt(pos) == '/' || s.charAt(pos) == '%')) {
                    char op = s.charAt(pos++);
                    double right = parseFactor();
                    value = switch (op) {
                        case '*' -> value * right;
                        case '/' -> value / right;
                        default -> value % right;
                    };
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipSpaces();
            if (pos >= s.length()) throw new NumberFormatException("unexpected end");
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                double value = parseExpression();
                skipSpaces();
                if (pos >= s.length() || s.charAt(pos) != ')') throw new NumberFormatException("missing )");
                pos++;
                return value;
            }
            if (c == '+' || c == '-') {
                pos++;
                double value = parseFactor();
                return c == '-' ? -value : value;
            }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new NumberFormatException("expect number at " + pos);
            return Double.parseDouble(s.substring(start, pos));
        }

        private void skipSpaces() {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
        }
    }

    // 供调试面板展示等场景：判断字符串是否像算式（含运算符），纯数字返回 false
    public static boolean isExpression(String text) {
        if (text == null) return false;
        for (char c : new char[]{'+', '-', '*', '/', '%', '(', ')'}) {
            if (text.indexOf(c) >= 0) return true;
        }
        return false;
    }
}
