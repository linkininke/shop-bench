package com.shopbench.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 中文用单字加二字词，英文按单词。演示不依赖分词词典。 */
public final class Tokenizer {
    private Tokenizer() {}

    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String s = text.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (isCjk(ch)) {
                flushAscii(ascii, out);
                cjk.append(ch);
            } else if (Character.isLetterOrDigit(ch)) {
                flushCjk(cjk, out);
                ascii.append(ch);
            } else {
                flushAscii(ascii, out);
                flushCjk(cjk, out);
            }
        }
        flushAscii(ascii, out);
        flushCjk(cjk, out);
        return out;
    }

    private static void flushAscii(StringBuilder ascii, List<String> out) {
        if (!ascii.isEmpty()) {
            out.add(ascii.toString());
            ascii.setLength(0);
        }
    }

    private static void flushCjk(StringBuilder cjk, List<String> out) {
        int n = cjk.length();
        for (int i = 0; i < n; i++) {
            out.add(String.valueOf(cjk.charAt(i)));
            if (i + 1 < n) {
                out.add("" + cjk.charAt(i) + cjk.charAt(i + 1));
            }
        }
        cjk.setLength(0);
    }

    private static boolean isCjk(char ch) {
        return ch >= 0x4E00 && ch <= 0x9FFF;
    }
}
