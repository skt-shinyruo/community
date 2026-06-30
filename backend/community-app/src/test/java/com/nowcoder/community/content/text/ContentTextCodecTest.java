package com.nowcoder.community.content.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTextCodecTest {

    @Test
    void codecShouldEscapeOnWriteAndDecodeOnRead() {
        ContentTextCodec codec = new ContentTextCodec();

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
