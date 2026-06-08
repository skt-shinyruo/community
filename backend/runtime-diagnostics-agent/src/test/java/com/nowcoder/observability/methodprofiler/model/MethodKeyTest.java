package com.nowcoder.observability.methodprofiler.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MethodKeyTest {

    @Test
    void stableHashUsesClassMethodParametersAndReturnType() throws Exception {
        MethodKey firstKey = MethodKey.from(Sample.class, "work", String.class, new Class<?>[]{String.class});
        MethodKey sameFirstKey = MethodKey.from(Sample.class, "work", String.class, new Class<?>[]{String.class});
        MethodKey secondKey = MethodKey.from(Sample.class, "work", String.class, new Class<?>[]{Integer.class});

        assertThat(firstKey.className()).isEqualTo("com.nowcoder.observability.methodprofiler.model.MethodKeyTest$Sample");
        assertThat(firstKey.methodName()).isEqualTo("work");
        assertThat(firstKey.signatureHash()).isEqualTo(sameFirstKey.signatureHash());
        assertThat(firstKey.signatureHash()).isNotEqualTo(secondKey.signatureHash());
        assertThat(firstKey.signatureHash()).hasSize(16);
    }

    @Test
    void stableHashCanUseByteBuddyOriginStrings() {
        MethodKey firstKey = MethodKey.from("com.example.Service", "work", "(Ljava/lang/String;)Ljava/lang/String;");
        MethodKey sameFirstKey = MethodKey.from("com.example.Service", "work", "(Ljava/lang/String;)Ljava/lang/String;");
        MethodKey secondKey = MethodKey.from("com.example.Service", "work", "(Ljava/lang/Integer;)Ljava/lang/String;");

        assertThat(firstKey.signatureHash()).isEqualTo(sameFirstKey.signatureHash());
        assertThat(firstKey.signatureHash()).isNotEqualTo(secondKey.signatureHash());
    }

    static class Sample {
        String work(String value) {
            return value;
        }

        String work(Integer value) {
            return String.valueOf(value);
        }
    }
}
