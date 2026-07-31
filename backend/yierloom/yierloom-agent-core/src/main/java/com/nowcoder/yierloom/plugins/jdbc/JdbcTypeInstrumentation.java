package com.nowcoder.yierloom.plugins.jdbc;

import com.nowcoder.yierloom.sdk.AdviceTransformers;
import com.nowcoder.yierloom.sdk.TypeInstrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public final class JdbcTypeInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
        return not(nameStartsWith("java."))
                .and(not(nameStartsWith("javax.")))
                .and(not(nameStartsWith("sun.")))
                .and(hasSuperType(named("java.sql.Statement")));
    }

    @Override
    public AgentBuilder.Transformer transformer() {
        return AdviceTransformers.forAdvice(
                JdbcStatementAdvice.class,
                named("execute")
                        .or(named("executeQuery"))
                        .or(named("executeUpdate"))
                        .or(named("executeLargeUpdate"))
                        .or(named("executeBatch")));
    }
}
