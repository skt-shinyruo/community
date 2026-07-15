package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.market.domain.model.MarketWalletAction;
import com.nowcoder.community.market.exception.MarketErrorCode;
import com.nowcoder.community.market.infrastructure.persistence.MyBatisMarketWalletActionRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketWalletActionDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketWalletActionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketWalletActionApplicationServiceTest {

    @Test
    void enqueueEscrowShouldUseOrderIdBasedRequestId() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        UuidV7Generator idGenerator = new UuidV7Generator();
        UUID orderId = uuid(2);
        when(mapper.insert(any(MarketWalletActionDataObject.class))).thenReturn(1);

        MarketWalletActionApplicationService service = new MarketWalletActionApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                idGenerator
        );
        service.enqueueEscrow(orderId, uuid(9), uuid(7), 12_900L);

        ArgumentCaptor<MarketWalletActionDataObject> captor = ArgumentCaptor.forClass(MarketWalletActionDataObject.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getActionId()).isNotNull();
        assertThat(captor.getValue().getActionId().version()).isEqualTo(7);
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(captor.getValue().getRequestId()).isEqualTo("market-order:" + orderId + ":escrow");
        assertThat(captor.getValue().getWalletBizId()).isEqualTo("market-order:" + orderId);
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getActionType()).isEqualTo("ESCROW");
    }

    @Test
    void duplicateInsertMustReloadAndValidateThePersistedActionFingerprint() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        UUID orderId = uuid(20);
        UUID buyerUserId = uuid(9);
        UUID sellerUserId = uuid(7);
        String requestId = "market-order:" + orderId + ":escrow";
        MarketWalletAction conflicting = action(orderId, requestId, buyerUserId, sellerUserId, 99_999L);
        when(mapper.selectByRequestId(requestId)).thenReturn(null, MarketWalletActionDataObject.from(conflicting));
        when(mapper.insert(any(MarketWalletActionDataObject.class)))
                .thenThrow(new DuplicateKeyException("duplicate market wallet action"));
        MarketWalletActionApplicationService service = new MarketWalletActionApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                new UuidV7Generator()
        );

        assertThatThrownBy(() -> service.enqueueEscrow(orderId, buyerUserId, sellerUserId, 12_900L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.REQUEST_REPLAY_CONFLICT));
    }

    @Test
    void unknownIntegrityFailureMustPropagateWithoutAFalseReplaySuccess() {
        MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
        UUID orderId = uuid(21);
        String requestId = "market-order:" + orderId + ":escrow";
        DataIntegrityViolationException unknown = new DataIntegrityViolationException("unknown wallet action constraint");
        when(mapper.selectByRequestId(requestId)).thenReturn(null);
        when(mapper.insert(any(MarketWalletActionDataObject.class))).thenThrow(unknown);
        MarketWalletActionApplicationService service = new MarketWalletActionApplicationService(
                new MyBatisMarketWalletActionRepository(mapper),
                new UuidV7Generator()
        );

        assertThatThrownBy(() -> service.enqueueEscrow(orderId, uuid(9), uuid(7), 12_900L))
                .isSameAs(unknown);
    }

    private MarketWalletAction action(
            UUID orderId,
            String requestId,
            UUID actorUserId,
            UUID counterpartyUserId,
            long amount
    ) {
        MarketWalletAction action = new MarketWalletAction();
        action.setActionId(uuid(22));
        action.setOrderId(orderId);
        action.setActionType("ESCROW");
        action.setRequestId(requestId);
        action.setWalletBizId("market-order:" + orderId);
        action.setActorUserId(actorUserId);
        action.setCounterpartyUserId(counterpartyUserId);
        action.setAmount(amount);
        action.setStatus("PENDING");
        return action;
    }
}
