package com.nowcoder.community.content.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTextCodecTest {

    @Test
    void codecShouldLeaveTextUntouched() {
        ContentTextCodec codec = new ContentTextCodec();

        assertThat(codec.escapeOnWrite(null)).isNull();
        assertThat(codec.escapeOnWrite("")).isEqualTo("");
        assertThat(codec.escapeOnWrite("A&B")).isEqualTo("A&B");
        assertThat(codec.escapeOnWrite("&lt;")).isEqualTo("&lt;");
        assertThat(codec.decodeOnRead(null)).isNull();
        assertThat(codec.decodeOnRead("")).isEqualTo("");
        assertThat(codec.decodeOnRead("&lt;tag&gt;")).isEqualTo("&lt;tag&gt;");
        assertThat(codec.decodeOnRead("A&amp;B")).isEqualTo("A&amp;B");
    }
}
