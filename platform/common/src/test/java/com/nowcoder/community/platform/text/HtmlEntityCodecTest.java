package com.nowcoder.community.platform.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlEntityCodecTest {

    @Test
    void escapeAmpersandShouldOnlyEscapeAmpersand() {
        assertThat(HtmlEntityCodec.escapeAmpersand(null)).isNull();
        assertThat(HtmlEntityCodec.escapeAmpersand("")).isEqualTo("");
        assertThat(HtmlEntityCodec.escapeAmpersand("abc")).isEqualTo("abc");
        assertThat(HtmlEntityCodec.escapeAmpersand("A&B")).isEqualTo("A&amp;B");
        assertThat(HtmlEntityCodec.escapeAmpersand("&lt;")).isEqualTo("&amp;lt;");
    }

    @Test
    void unescapeBasicShouldDecodeWhitelistOnce() {
        assertThat(HtmlEntityCodec.unescapeBasic(null)).isNull();
        assertThat(HtmlEntityCodec.unescapeBasic("")).isEqualTo("");
        assertThat(HtmlEntityCodec.unescapeBasic("plain")).isEqualTo("plain");

        assertThat(HtmlEntityCodec.unescapeBasic("&lt;tag&gt;")).isEqualTo("<tag>");
        assertThat(HtmlEntityCodec.unescapeBasic("&quot;x&quot; &#39;y&#39;")).isEqualTo("\"x\" 'y'");
        assertThat(HtmlEntityCodec.unescapeBasic("&apos;z&apos;")).isEqualTo("'z'");
        assertThat(HtmlEntityCodec.unescapeBasic("A&amp;B")).isEqualTo("A&B");
    }

    @Test
    void unescapeBasicShouldDecodeAmpLastToPreserveLiteralEntities() {
        // "&amp;lt;" 应解到 "&lt;"（而不是直接 "<"）
        assertThat(HtmlEntityCodec.unescapeBasic("&amp;lt;")).isEqualTo("&lt;");
        assertThat(HtmlEntityCodec.unescapeBasic("&amp;lt;tag&amp;gt;")).isEqualTo("&lt;tag&gt;");
        assertThat(HtmlEntityCodec.unescapeBasic("&amp;quot;x&amp;quot;")).isEqualTo("&quot;x&quot;");
    }
}

