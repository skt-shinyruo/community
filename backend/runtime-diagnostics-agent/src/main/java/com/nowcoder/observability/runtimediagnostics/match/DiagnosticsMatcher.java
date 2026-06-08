package com.nowcoder.observability.runtimediagnostics.match;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.regex.Pattern;

public class DiagnosticsMatcher {

    private static final List<String> HARD_EXCLUDES = List.of(
            "java.*",
            "javax.*",
            "jakarta.*",
            "sun.*",
            "jdk.*",
            "org.slf4j.*",
            "ch.qos.logback.*",
            "net.bytebuddy.*",
            "com.nowcoder.observability.runtimediagnostics.*"
    );

    private final DiagnosticsConfig config;

    public DiagnosticsMatcher(DiagnosticsConfig config) {
        this.config = config;
    }

    public boolean shouldInstrumentClass(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        if (matchesAny(HARD_EXCLUDES, className) || matchesAny(config.excludes(), className)) {
            return false;
        }
        return matchesAny(config.includes(), className);
    }

    public boolean shouldInstrumentMethod(Method method) {
        if (method == null) {
            return false;
        }
        int modifiers = method.getModifiers();
        return !Modifier.isAbstract(modifiers)
                && !Modifier.isNative(modifiers)
                && !method.isBridge()
                && !method.isSynthetic();
    }

    private boolean matchesAny(List<String> patterns, String className) {
        for (String pattern : patterns) {
            if (matches(pattern, className)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String pattern, String className) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        String trimmed = pattern.trim();
        if ("*".equals(trimmed)) {
            return true;
        }
        if (trimmed.endsWith(".*")) {
            String prefix = trimmed.substring(0, trimmed.length() - 1);
            return className.startsWith(prefix);
        }
        if (trimmed.indexOf('*') >= 0) {
            return Pattern.matches(globToRegex(trimmed), className);
        }
        return className.equals(trimmed);
    }

    private String globToRegex(String pattern) {
        StringBuilder regex = new StringBuilder(pattern.length() + 8);
        regex.append('^');
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '*') {
                regex.append(".*");
            } else if (".[]{}()+-^$?\\".indexOf(ch) >= 0) {
                regex.append('\\').append(ch);
            } else {
                regex.append(ch);
            }
        }
        regex.append('$');
        return regex.toString();
    }
}
