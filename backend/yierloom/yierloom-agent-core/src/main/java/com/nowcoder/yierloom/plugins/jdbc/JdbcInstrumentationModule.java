package com.nowcoder.yierloom.plugins.jdbc;

import java.util.List;
import java.util.Set;

import com.nowcoder.yierloom.sdk.InstrumentationModule;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;

public final class JdbcInstrumentationModule implements InstrumentationModule {
    private final List<TypeInstrumentation> typeInstrumentations =
            List.of(new JdbcTypeInstrumentation());

    @Override
    public String id() {
        return "jdbc";
    }

    @Override
    public List<? extends TypeInstrumentation> typeInstrumentations() {
        return typeInstrumentations;
    }

    @Override
    public Set<String> helperClassNames() {
        return Set.of(JdbcObservationHelper.class.getName());
    }
}
