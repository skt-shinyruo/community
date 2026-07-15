package com.nowcoder.community.market;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class MarketOrderBoundaryContractTest {

    @Test
    void aggregateMustNotExposePersistenceMutationOrInheritItsRowType() {
        List<String> publicSetters = Arrays.stream(MarketOrder.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> name.matches("set[A-Z].*"))
                .sorted()
                .toList();

        assertSoftly(softly -> {
            softly.assertThat(publicSetters)
                    .as("MarketOrder state must only change through domain behavior")
                    .isEmpty();
            softly.assertThat(MarketOrder.class.isAssignableFrom(MarketOrderDataObject.class))
                    .as("persistence rows must not be MarketOrder aggregates")
                    .isFalse();
            softly.assertThat(Arrays.stream(MarketOrderDataObject.class.getDeclaredMethods())
                            .map(Method::getName))
                    .as("MarketOrderDataObject must explicitly reconstruct the aggregate")
                    .contains("toDomain");
        });
    }

    @Test
    void repositoryAndMapperMustExposeOneGenericCasTransitionEntry() {
        List<Method> repositoryApplyMethods = methodsNamed(MarketOrderRepository.class, "apply");
        List<String> repositoryLegacyWrites = legacyStateWrites(MarketOrderRepository.class);
        List<Method> mapperApplyMethods = methodsNamed(MarketOrderMapper.class, "apply");
        List<String> mapperLegacyWrites = legacyStateWrites(MarketOrderMapper.class);

        assertSoftly(softly -> {
            softly.assertThat(repositoryApplyMethods)
                    .as("MarketOrderRepository.apply(MarketOrderTransition)")
                    .hasSize(1);
            if (repositoryApplyMethods.size() == 1) {
                Method method = repositoryApplyMethods.get(0);
                softly.assertThat(method.getParameterTypes())
                        .containsExactly(MarketOrderTransition.class);
                softly.assertThat(method.getReturnType().isEnum())
                        .as("apply must return a semantic enum, not a raw update count")
                        .isTrue();
                if (method.getReturnType().isEnum()) {
                    softly.assertThat(Arrays.stream(method.getReturnType().getEnumConstants())
                                    .map(Object::toString))
                            .contains("APPLIED", "STALE");
                }
            }
            softly.assertThat(repositoryLegacyWrites)
                    .as("repository must not keep SQL-shaped markXxx/changeStatus entry points")
                    .isEmpty();
            softly.assertThat(mapperApplyMethods)
                    .as("MarketOrderMapper must have one generic apply entry")
                    .hasSize(1);
            softly.assertThat(mapperLegacyWrites)
                    .as("mapper must not hard-code the state graph in markXxx methods")
                    .isEmpty();
        });
    }

    private static List<Method> methodsNamed(Class<?> owner, String name) {
        return Arrays.stream(owner.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .toList();
    }

    private static List<String> legacyStateWrites(Class<?> owner) {
        return Arrays.stream(owner.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("mark") || name.equals("changeStatus") || name.equals("updateStatus"))
                .sorted()
                .toList();
    }
}
