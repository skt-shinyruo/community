package com.nowcoder.yierloom.plugins.support;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobClassMatcherTest {

    @Test
    void appliesIncludesAndLetsUserExcludesWin() {
        GlobClassMatcher matcher = new GlobClassMatcher(
                List.of("com.example.*", "org.acme.Service"),
                List.of("com.example.internal.*"));

        assertThat(matcher.matches("com.example.Service")).isTrue();
        assertThat(matcher.matches("org.acme.Service")).isTrue();
        assertThat(matcher.matches("com.example.internal.Secret")).isFalse();
        assertThat(matcher.matches("org.acme.Other")).isFalse();
    }

    @Test
    void treatsEveryRegexMetacharacterAsAClassNameLiteral() {
        GlobClassMatcher matcher = new GlobClassMatcher(
                List.of("com.example.$Proxy|Service[0](x)+?^{}\\*"),
                List.of());

        assertThat(matcher.matches("com.example.$Proxy|Service[0](x)+?^{}\\42")).isTrue();
        assertThat(matcher.matches("com.example.Service42")).isFalse();
    }

    @Test
    void alwaysAppliesAgentAndPlatformHardExcludes() {
        GlobClassMatcher matcher = new GlobClassMatcher(List.of("*"), List.of());

        assertThat(List.of(
                "java.lang.String",
                "javax.sql.DataSource",
                "jakarta.servlet.Servlet",
                "sun.misc.Unsafe",
                "com.sun.org.apache.xerces.internal.parsers.SAXParser",
                "jdk.internal.misc.Unsafe",
                "org.slf4j.Logger",
                "ch.qos.logback.classic.Logger",
                "net.bytebuddy.ByteBuddy",
                "com.nowcoder.yierloom.plugins.method.MethodPlugin"
        )).allMatch(className -> !matcher.matches(className));
        assertThat(matcher.matches("com.nowcoder.community.DiscussPost")).isTrue();
    }

    @Test
    void emptyIncludesNormalizeToMatchAll() {
        assertThat(new GlobClassMatcher(List.of(), List.of())
                .matches("com.example.Service")).isTrue();
    }
}
