package com.nowcoder.community.search.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KeywordHighlightSupport {

    private static final int MAX_TOKENS = 6;
    private static final int MAX_TOKEN_CHARS = 32;

    private KeywordHighlightSupport() {
    }

    public static String highlight(String text, String keyword) {
        if (!hasText(text) || !hasText(keyword)) {
            return text;
        }
        List<String> tokens = tokenize(keyword);
        if (tokens.isEmpty()) {
            return text;
        }
        Pattern pattern = compileTokenPattern(tokens);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text;
        }

        matcher.reset();
        StringBuffer sb = new StringBuffer(Math.max(16, text.length() + 32));
        while (matcher.find()) {
            String hit = matcher.group(1);
            matcher.appendReplacement(sb, "<em>" + Matcher.quoteReplacement(hit) + "</em>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    static List<String> tokenize(String keyword) {
        if (!hasText(keyword)) {
            return List.of();
        }
        String raw = keyword.trim();
        if (raw.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String part : raw.split("\\s+")) {
            if (!hasText(part)) {
                continue;
            }
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.length() > MAX_TOKEN_CHARS) {
                t = t.substring(0, MAX_TOKEN_CHARS);
            }
            String k = t.toLowerCase(Locale.ROOT);
            if (dedup.contains(k)) {
                continue;
            }
            dedup.add(k);
            if (dedup.size() >= MAX_TOKENS) {
                break;
            }
        }

        if (dedup.isEmpty()) {
            return List.of();
        }
        // We keep normalized tokens because matching is case-insensitive anyway.
        return new ArrayList<>(dedup);
    }

    private static Pattern compileTokenPattern(List<String> tokens) {
        StringBuilder sb = new StringBuilder(64);
        for (String t : tokens) {
            if (!hasText(t)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('|');
            }
            sb.append(Pattern.quote(t));
        }
        if (sb.isEmpty()) {
            // no tokens: compile something that never matches
            return Pattern.compile("(?!x)x");
        }
        return Pattern.compile("(" + sb + ")", Pattern.CASE_INSENSITIVE);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
