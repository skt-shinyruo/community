package com.nowcoder.yierloom.sdk;

import java.lang.annotation.Annotation;
import java.util.Objects;

public record AdviceBinding(
        Class<? extends Annotation> annotationType,
        Object value
) {
    public AdviceBinding {
        Objects.requireNonNull(annotationType);
        Objects.requireNonNull(value);
        if (!(value instanceof Boolean || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long || value instanceof Float
                || value instanceof Double || value instanceof Character || value instanceof String
                || value instanceof Class<?> || value instanceof Enum<?>)) {
            throw new IllegalArgumentException("unsupported Advice binding constant");
        }
    }
}
