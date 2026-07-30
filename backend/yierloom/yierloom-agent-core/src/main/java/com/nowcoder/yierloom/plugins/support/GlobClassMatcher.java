package com.nowcoder.yierloom.plugins.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public final class GlobClassMatcher extends ElementMatcher.Junction.AbstractBase<TypeDescription> {
    private static final List<String> HARD_EXCLUDES = List.of(
            "java.*",
            "javax.*",
            "jakarta.*",
            "sun.*",
            "com.sun.*",
            "jdk.*",
            "org.slf4j.*",
            "ch.qos.logback.*",
            "net.bytebuddy.*",
            "com.nowcoder.yierloom.*"
    );

    private final List<Pattern> includes;
    private final List<Pattern> excludes;

    public GlobClassMatcher(List<String> includes, List<String> excludes) {
        this.includes = compile(normalizeIncludes(includes));
        List<String> allExcludes = new ArrayList<>(HARD_EXCLUDES);
        allExcludes.addAll(Objects.requireNonNull(excludes, "excludes"));
        this.excludes = compile(allExcludes);
    }

    @Override
    public boolean matches(TypeDescription target) {
        return target != null && matches(target.getName());
    }

    public boolean matches(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        return !matchesAny(excludes, className) && matchesAny(includes, className);
    }

    private static List<String> normalizeIncludes(List<String> values) {
        Objects.requireNonNull(values, "includes");
        return values.stream().anyMatch(value -> value != null && !value.isBlank())
                ? values
                : List.of("*");
    }

    private static List<Pattern> compile(List<String> globs) {
        List<Pattern> patterns = new ArrayList<>();
        for (String glob : globs) {
            if (glob != null && !glob.isBlank()) {
                patterns.add(Pattern.compile(toRegex(glob.trim())));
            }
        }
        return List.copyOf(patterns);
    }

    private static boolean matchesAny(List<Pattern> patterns, String className) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(className).matches());
    }

    private static String toRegex(String glob) {
        StringBuilder regex = new StringBuilder(glob.length() + 16).append('^');
        int literalStart = 0;
        for (int index = 0; index < glob.length(); index++) {
            if (glob.charAt(index) != '*') {
                continue;
            }
            if (literalStart < index) {
                regex.append(Pattern.quote(glob.substring(literalStart, index)));
            }
            regex.append(".*");
            literalStart = index + 1;
        }
        if (literalStart < glob.length()) {
            regex.append(Pattern.quote(glob.substring(literalStart)));
        }
        return regex.append('$').toString();
    }
}
