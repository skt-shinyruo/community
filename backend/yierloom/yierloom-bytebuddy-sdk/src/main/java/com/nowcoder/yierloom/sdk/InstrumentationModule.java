package com.nowcoder.yierloom.sdk;

import java.util.List;
import java.util.Set;

public interface InstrumentationModule {
    String id();

    List<? extends TypeInstrumentation> typeInstrumentations();

    default Set<String> helperClassNames() {
        return Set.of();
    }
}
