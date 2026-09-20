package com.nowcoder.community.content.infrastructure.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveFilterTest {

    private final SensitiveFilter filter = createFilter();

    private static SensitiveFilter createFilter() {
        SensitiveFilter filter = new SensitiveFilter(new MockEnvironment(), null);
        filter.init();
        return filter;
    }

    @Test
    void filterShouldReplaceDictionaryWord() {
        assertThat(filter.filter("this is spam here")).isEqualTo("this is *** here");
    }

    @Test
    void filterShouldRecognizeWordWithInsertedSymbols() {
        assertThat(filter.filter("this is s-p-a-m here")).isEqualTo("this is *** here");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void filterShouldKeepNullEmptyOrBlankInput(String input) {
        assertThat(filter.filter(input)).isEqualTo(input);
    }
}
