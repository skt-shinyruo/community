package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketWalletActionDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketWalletActionMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketSemanticCreateRepositoryTest {

    @ParameterizedTest(name = "{0}: insert -> CREATED")
    @MethodSource("marketRepositories")
    void createShouldReturnCreatedWithTheCandidateAggregate(Scenario scenario) throws Exception {
        scenario.stubCreated().run();

        Object outcome = invokeCreate(scenario);

        assertOutcome(scenario, outcome, "CREATED", scenario.candidate());
        scenario.verifyNoReload().run();
    }

    @ParameterizedTest(name = "{0}: duplicate -> ALREADY_EXISTS + reload")
    @MethodSource("marketRepositories")
    void duplicateShouldReloadOwnerAggregateAndReturnAlreadyExists(Scenario scenario) throws Exception {
        scenario.stubDuplicate().run();

        Object outcome = invokeCreate(scenario);

        assertOutcome(scenario, outcome, "ALREADY_EXISTS", scenario.existing());
        scenario.verifyReload().run();
    }

    @ParameterizedTest(name = "{0}: unknown integrity failure propagates")
    @MethodSource("marketRepositories")
    void unknownIntegrityFailureMustNotMasqueradeAsAlreadyExists(Scenario scenario) {
        scenario.stubUnknownFailure().run();

        assertThatThrownBy(() -> invokeCreate(scenario))
                .isInstanceOf(InvocationTargetException.class)
                .satisfies(error -> assertThat(error.getCause()).isSameAs(scenario.unknownFailure()));
    }

    private static Stream<Arguments> marketRepositories() {
        return Stream.of(orderScenario(), walletActionScenario()).map(Arguments::of);
    }

    private static Scenario orderScenario() {
        UUID buyerUserId = UUID.fromString("00000000-0000-7000-8000-000000000201");
        String requestId = "market-order:semantic-create";
        MarketOrder candidate = mock(MarketOrder.class);
        MarketOrder existing = mock(MarketOrder.class);
        MarketOrderDataObject existingRow = mock(MarketOrderDataObject.class);
        MarketOrderMapper mapper = mock(MarketOrderMapper.class);
        when(candidate.getBuyerUserId()).thenReturn(buyerUserId);
        when(candidate.getRequestId()).thenReturn(requestId);
        when(existingRow.toDomain()).thenReturn(existing);
        DataIntegrityViolationException unknown = unknownFailure("market order");
        return new Scenario(
                "market order",
                new MyBatisMarketOrderRepository(mapper),
                MarketOrder.class,
                candidate,
                existing,
                () -> when(mapper.insert(any(MarketOrderDataObject.class))).thenReturn(1),
                () -> {
                    doThrow(new DuplicateKeyException("duplicate market order request"))
                            .when(mapper).insert(any(MarketOrderDataObject.class));
                    when(mapper.selectByBuyerUserIdAndRequestIdForUpdate(buyerUserId, requestId))
                            .thenReturn(existingRow);
                },
                () -> doThrow(unknown).when(mapper).insert(any(MarketOrderDataObject.class)),
                () -> verify(mapper).selectByBuyerUserIdAndRequestIdForUpdate(buyerUserId, requestId),
                () -> verify(mapper, never()).selectByBuyerUserIdAndRequestIdForUpdate(buyerUserId, requestId),
                unknown
        );
    }

    private static Scenario walletActionScenario() {
        String requestId = "market-wallet-action:semantic-create";
        MarketWalletAction candidate = mock(MarketWalletAction.class);
        MarketWalletActionDataObject existing = mock(MarketWalletActionDataObject.class);
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        when(candidate.getRequestId()).thenReturn(requestId);
        DataIntegrityViolationException unknown = unknownFailure("market wallet action");
        return new Scenario(
                "market wallet action",
                new MyBatisMarketWalletActionRepository(mapper),
                MarketWalletAction.class,
                candidate,
                existing,
                () -> when(mapper.insert(any(MarketWalletActionDataObject.class))).thenReturn(1),
                () -> {
                    doThrow(new DuplicateKeyException("duplicate market wallet action request"))
                            .when(mapper).insert(any(MarketWalletActionDataObject.class));
                    when(mapper.selectByRequestId(requestId)).thenReturn(existing);
                },
                () -> doThrow(unknown).when(mapper).insert(any(MarketWalletActionDataObject.class)),
                () -> verify(mapper).selectByRequestId(requestId),
                () -> verify(mapper, never()).selectByRequestId(requestId),
                unknown
        );
    }

    private static Object invokeCreate(Scenario scenario) throws Exception {
        Method create = Arrays.stream(scenario.repository().getClass().getMethods())
                .filter(method -> method.getName().equals("create"))
                .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{scenario.aggregateType()}))
                .findFirst()
                .orElse(null);
        assertThat(create)
                .as("%s must expose create(%s) semantic outcome", scenario.name(), scenario.aggregateType().getSimpleName())
                .isNotNull();
        return create.invoke(scenario.repository(), scenario.candidate());
    }

    private static void assertOutcome(Scenario scenario, Object outcome, String status, Object aggregate) throws Exception {
        assertThat(outcome).as(scenario.name() + " creation outcome").isNotNull();
        assertThat(outcome.getClass().getPackageName())
                .as("semantic outcome must be owned by market domain repository")
                .startsWith("com.nowcoder.community.market.domain.repository");
        assertThat(invokeAccessor(outcome, "status")).hasToString(status);
        assertThat(invokeAccessor(outcome, "aggregate")).isSameAs(aggregate);
    }

    private static Object invokeAccessor(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static DataIntegrityViolationException unknownFailure(String label) {
        return new DataIntegrityViolationException("unknown " + label + " integrity failure");
    }

    private record Scenario(
            String name,
            Object repository,
            Class<?> aggregateType,
            Object candidate,
            Object existing,
            Runnable stubCreated,
            Runnable stubDuplicate,
            Runnable stubUnknownFailure,
            Runnable verifyReload,
            Runnable verifyNoReload,
            DataIntegrityViolationException unknownFailure
    ) {
        @Override
        public String toString() {
            return name;
        }
    }
}
