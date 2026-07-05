package com.nowcoder.community.content.infrastructure.text;

import com.nowcoder.community.content.application.ContentTextCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringHtmlContentTextCodecTest {

    private final ContentTextCodec codec = new SpringHtmlContentTextCodec();

    @Test
    void codecShouldEscapeOnWriteAndDecodeOnRead() {
        assertThat(codec.escapeOnWrite(null)).isNull();
        assertThat(codec.escapeOnWrite("")).isEqualTo("");
        assertThat(codec.escapeOnWrite("<tag>A&B</tag>")).isEqualTo("&lt;tag&gt;A&amp;B&lt;/tag&gt;");
        assertThat(codec.escapeOnWrite("&lt;")).isEqualTo("&amp;lt;");
        assertThat(codec.decodeOnRead(null)).isNull();
        assertThat(codec.decodeOnRead("")).isEqualTo("");
        assertThat(codec.decodeOnRead("&lt;tag&gt;")).isEqualTo("<tag>");
        assertThat(codec.decodeOnRead("A&amp;B")).isEqualTo("A&B");
    }
}
