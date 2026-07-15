package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.wallet.domain.model.RechargeOrder;
import com.nowcoder.community.wallet.domain.model.TransferOrder;
import com.nowcoder.community.wallet.domain.model.WalletAccount;
import com.nowcoder.community.wallet.domain.model.WalletAdminAction;
import com.nowcoder.community.wallet.domain.model.WalletTxn;
import com.nowcoder.community.wallet.domain.model.WithdrawOrder;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.RechargeOrderDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.TransferOrderDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletAccountDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletAdminActionDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletTxnDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WithdrawOrderDataObject;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.RechargeOrderMapper;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.TransferOrderMapper;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletAccountMapper;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletAdminActionMapper;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletEntryMapper;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletTxnMapper;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WithdrawOrderMapper;
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

class WalletSemanticCreateRepositoryTest {

    @ParameterizedTest(name = "{0}: insert -> CREATED")
    @MethodSource("walletRepositories")
    void createShouldReturnCreatedWithTheCandidateAggregate(Scenario scenario) throws Exception {
        scenario.stubCreated().run();

        Object outcome = invokeCreate(scenario);

        assertOutcome(scenario, outcome, "CREATED", scenario.candidate());
        scenario.verifyNoReload().run();
    }

    @ParameterizedTest(name = "{0}: duplicate -> ALREADY_EXISTS + reload")
    @MethodSource("walletRepositories")
    void duplicateShouldReloadOwnerAggregateAndReturnAlreadyExists(Scenario scenario) throws Exception {
        scenario.stubDuplicate().run();

        Object outcome = invokeCreate(scenario);

        assertOutcome(scenario, outcome, "ALREADY_EXISTS", scenario.existing());
        scenario.verifyReload().run();
    }

    @ParameterizedTest(name = "{0}: unknown integrity failure propagates")
    @MethodSource("walletRepositories")
    void unknownIntegrityFailureMustNotMasqueradeAsAlreadyExists(Scenario scenario) {
        scenario.stubUnknownFailure().run();

        assertThatThrownBy(() -> invokeCreate(scenario))
                .isInstanceOf(InvocationTargetException.class)
                .satisfies(error -> assertThat(error.getCause()).isSameAs(scenario.unknownFailure()));
    }

    private static Stream<Arguments> walletRepositories() {
        return Stream.of(
                rechargeScenario(),
                withdrawScenario(),
                transferScenario(),
                accountScenario(),
                ledgerScenario(),
                adminActionScenario()
        ).map(Arguments::of);
    }

    private static Scenario rechargeScenario() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000101");
        String requestId = "recharge:semantic-create";
        RechargeOrder candidate = mock(RechargeOrder.class);
        RechargeOrderDataObject existing = mock(RechargeOrderDataObject.class);
        RechargeOrderMapper mapper = mock(RechargeOrderMapper.class);
        when(candidate.getUserId()).thenReturn(userId);
        when(candidate.getRequestId()).thenReturn(requestId);
        DataIntegrityViolationException unknown = unknownFailure("recharge");
        return new Scenario(
                "recharge order",
                new MyBatisRechargeOrderRepository(mapper),
                RechargeOrder.class,
                candidate,
                existing,
                () -> when(mapper.insert(any(RechargeOrderDataObject.class))).thenReturn(1),
                () -> {
                    doThrow(new DuplicateKeyException("duplicate recharge request"))
                            .when(mapper).insert(any(RechargeOrderDataObject.class));
                    when(mapper.selectByUserIdAndRequestId(userId, requestId)).thenReturn(existing);
                },
                () -> doThrow(unknown).when(mapper).insert(any(RechargeOrderDataObject.class)),
                () -> verify(mapper).selectByUserIdAndRequestId(userId, requestId),
                () -> verify(mapper, never()).selectByUserIdAndRequestId(userId, requestId),
                unknown
        );
    }

    private static Scenario withdrawScenario() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000102");
        String requestId = "withdraw:semantic-create";
        WithdrawOrder candidate = mock(WithdrawOrder.class);
        WithdrawOrderDataObject existing = mock(WithdrawOrderDataObject.class);
        WithdrawOrderMapper mapper = mock(WithdrawOrderMapper.class);
        when(candidate.getUserId()).thenReturn(userId);
        when(candidate.getRequestId()).thenReturn(requestId);
        DataIntegrityViolationException unknown = unknownFailure("withdraw");
        return new Scenario(
                "withdraw order",
                new MyBatisWithdrawOrderRepository(mapper),
                WithdrawOrder.class,
                candidate,
                existing,
                () -> when(mapper.insert(any(WithdrawOrderDataObject.class))).thenReturn(1),
                () -> {
                    doThrow(new DuplicateKeyException("duplicate withdraw request"))
                            .when(mapper).insert(any(WithdrawOrderDataObject.class));
                    when(mapper.selectByUserIdAndRequestId(userId, requestId)).thenReturn(existing);
                },
                () -> doThrow(unknown).when(mapper).insert(any(WithdrawOrderDataObject.class)),
                () -> verify(mapper).selectByUserIdAndRequestId(userId, requestId),
                () -> verify(mapper, never()).selectByUserIdAndRequestId(userId, requestId),
                unknown
        );
    }

    private static Scenario transferScenario() {
        UUID fromUserId = UUID.fromString("00000000-0000-7000-8000-000000000103");
        String requestId = "transfer:semantic-create";
        TransferOrder candidate = mock(TransferOrder.class);
        TransferOrderDataObject existing = mock(TransferOrderDataObject.class);
        TransferOrderMapper mapper = mock(TransferOrderMapper.class);
        when(candidate.getFromUserId()).thenReturn(fromUserId);
        when(candidate.getRequestId()).thenReturn(requestId);
        DataIntegrityViolationException unknown = unknownFailure("transfer");
        return new Scenario(
                "transfer order",
                new MyBatisTransferOrderRepository(mapper),
                TransferOrder.class,
                candidate,
                existing,
                () -> when(mapper.insert(any(TransferOrderDataObject.class))).thenReturn(1),
                () -> {
                    doThrow(new DuplicateKeyException("duplicate transfer request"))
                            .when(mapper).insert(any(TransferOrderDataObject.class));
                    when(mapper.selectByFromUserIdAndRequestId(fromUserId, requestId)).thenReturn(existing);
                },
                () -> doThrow(unknown).when(mapper).insert(any(TransferOrderDataObject.class)),
                () -> verify(mapper).selectByFromUserIdAndRequestId(fromUserId, requestId),
                () -> verify(mapper, never()).selectByFromUserIdAndRequestId(fromUserId, requestId),
                unknown
        );
    }

    private static Scenario accountScenario() {
        UUID ownerId = UUID.fromString("00000000-0000-7000-8000-000000000104");
        WalletAccount candidate = mock(WalletAccount.class);
        WalletAccount existing = WalletAccount.reconstitute(
                UUID.fromString("00000000-0000-7000-8000-000000000105"),
                "USER",
                ownerId,
                "USER_WALLET",
                120L,
                "ACTIVE",
                3L
        );
        WalletAccountDataObject existingRow = WalletAccountDataObject.from(existing);
        WalletAccountMapper mapper = mock(WalletAccountMapper.class);
        when(candidate.getOwnerType()).thenReturn("USER");
        when(candidate.getOwnerId()).thenReturn(ownerId);
        when(candidate.getAccountType()).thenReturn("USER_WALLET");
        DataIntegrityViolationException unknown = unknownFailure("wallet account");
        return new Scenario(
                "wallet account",
                new MyBatisWalletAccountRepository(mapper),
                WalletAccount.class,
                candidate,
                existing,
                () -> when(mapper.insert(any(WalletAccountDataObject.class))).thenReturn(1),
                () -> {
                    doThrow(new DuplicateKeyException("duplicate wallet owner"))
                            .when(mapper).insert(any(WalletAccountDataObject.class));
                    when(mapper.selectByOwner("USER", ownerId, "USER_WALLET")).thenReturn(existingRow);
                },
                () -> doThrow(unknown).when(mapper).insert(any(WalletAccountDataObject.class)),
                () -> verify(mapper).selectByOwner("USER", ownerId, "USER_WALLET"),
                () -> verify(mapper, never()).selectByOwner("USER", ownerId, "USER_WALLET"),
                unknown
        );
    }

    private static Scenario ledgerScenario() {
        String requestId = "wallet:txn:semantic-create";
        WalletTxn candidate = mock(WalletTxn.class);
        WalletTxnDataObject existing = mock(WalletTxnDataObject.class);
        WalletTxnMapper txnMapper = mock(WalletTxnMapper.class);
        WalletEntryMapper entryMapper = mock(WalletEntryMapper.class);
        when(candidate.getRequestId()).thenReturn(requestId);
        DataIntegrityViolationException unknown = unknownFailure("wallet transaction");
        return new Scenario(
                "wallet transaction",
                new MyBatisWalletLedgerRepository(txnMapper, entryMapper),
                WalletTxn.class,
                candidate,
                existing,
                () -> when(txnMapper.insert(any(WalletTxnDataObject.class))).thenReturn(1),
                () -> {
                    doThrow(new DuplicateKeyException("duplicate wallet transaction"))
                            .when(txnMapper).insert(any(WalletTxnDataObject.class));
                    when(txnMapper.selectByRequestId(requestId)).thenReturn(existing);
                },
                () -> doThrow(unknown).when(txnMapper).insert(any(WalletTxnDataObject.class)),
                () -> verify(txnMapper).selectByRequestId(requestId),
                () -> verify(txnMapper, never()).selectByRequestId(requestId),
                unknown
        );
    }

    private static Scenario adminActionScenario() {
        String requestId = "wallet-admin:semantic-create";
        WalletAdminAction candidate = mock(WalletAdminAction.class);
        WalletAdminActionDataObject existing = mock(WalletAdminActionDataObject.class);
        WalletAdminActionMapper mapper = mock(WalletAdminActionMapper.class);
        when(candidate.getRequestId()).thenReturn(requestId);
        DataIntegrityViolationException unknown = unknownFailure("wallet admin action");
        return new Scenario(
                "wallet admin action",
                new MyBatisWalletAdminActionRepository(mapper),
                WalletAdminAction.class,
                candidate,
                existing,
                () -> when(mapper.insert(any(WalletAdminActionDataObject.class))).thenReturn(1),
                () -> {
                    doThrow(new DuplicateKeyException("duplicate wallet admin action"))
                            .when(mapper).insert(any(WalletAdminActionDataObject.class));
                    when(mapper.selectByRequestId(requestId)).thenReturn(existing);
                },
                () -> doThrow(unknown).when(mapper).insert(any(WalletAdminActionDataObject.class)),
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
                .as("semantic outcome must be owned by wallet domain repository")
                .startsWith("com.nowcoder.community.wallet.domain.repository");
        assertThat(invokeAccessor(outcome, "status")).hasToString(status);
        Object actualAggregate = invokeAccessor(outcome, "aggregate");
        if (actualAggregate == aggregate) {
            return;
        }
        if (actualAggregate instanceof WalletAccount actual && aggregate instanceof WalletAccount expected) {
            assertThat(actual.getAccountId()).isEqualTo(expected.getAccountId());
            assertThat(actual.getOwnerType()).isEqualTo(expected.getOwnerType());
            assertThat(actual.getOwnerId()).isEqualTo(expected.getOwnerId());
            assertThat(actual.getAccountType()).isEqualTo(expected.getAccountType());
            assertThat(actual.getBalance()).isEqualTo(expected.getBalance());
            assertThat(actual.getStatus()).isEqualTo(expected.getStatus());
            assertThat(actual.getVersion()).isEqualTo(expected.getVersion());
            return;
        }
        assertThat(actualAggregate).isSameAs(aggregate);
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
