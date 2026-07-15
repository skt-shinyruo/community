package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.market.domain.model.MarketDeliveryMode;
import com.nowcoder.community.market.domain.model.MarketGoodsType;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderStatus;
import com.nowcoder.community.market.domain.model.MarketOrderTransition;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketOrderDataObject;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MarketOrderTransitionRepositoryTest {

    private static final UUID ORDER_ID = uuid(301);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketOrderMapper mapper;

    @Autowired
    private MarketOrderRepository repository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_shipment");
        jdbcTemplate.update("delete from market_dispute");
        jdbcTemplate.update("delete from market_delivery");
        jdbcTemplate.update("delete from market_wallet_action");
        jdbcTemplate.update("delete from market_order");
    }

    @Test
    void applyShouldPersistTransitionWhenExpectedStatusMatches() {
        Date autoConfirmAt = new Date(2_000L);
        mapper.insert(MarketOrderDataObject.from(orderIn(MarketOrderStatus.ESCROWED, null, null, null, null)));
        MarketOrderTransition transition = orderIn(
                MarketOrderStatus.ESCROWED,
                null,
                null,
                null,
                null
        ).markDelivered(autoConfirmAt);

        Object outcome = invokeApply(repository, transition);

        assertThat(outcome).hasToString("APPLIED");
        MarketOrderDataObject stored = mapper.selectById(ORDER_ID);
        assertThat(stored.getStatus()).isEqualTo(MarketOrderStatus.DELIVERED.code());
        assertThat(stored.getAutoConfirmAt()).hasSameTimeAs(autoConfirmAt);
    }

    @Test
    void staleExpectedStatusShouldReturnStaleAndUpdateZeroBusinessFields() {
        UUID escrowTxnId = uuid(401);
        UUID releaseTxnId = uuid(402);
        UUID refundTxnId = uuid(403);
        Date originalAutoConfirmAt = new Date(1_000L);
        mapper.insert(MarketOrderDataObject.from(orderIn(
                MarketOrderStatus.REFUND_PENDING,
                escrowTxnId,
                releaseTxnId,
                refundTxnId,
                originalAutoConfirmAt
        )));
        MarketOrderTransition staleTransition = orderIn(
                MarketOrderStatus.ESCROWED,
                null,
                null,
                null,
                null
        ).markDelivered(new Date(2_000L));

        Object outcome = invokeApply(repository, staleTransition);

        assertThat(outcome).hasToString("STALE");
        MarketOrderDataObject stored = mapper.selectById(ORDER_ID);
        assertThat(stored.getStatus()).isEqualTo(MarketOrderStatus.REFUND_PENDING.code());
        assertThat(stored.getEscrowTxnId()).isEqualTo(escrowTxnId);
        assertThat(stored.getReleaseTxnId()).isEqualTo(releaseTxnId);
        assertThat(stored.getRefundTxnId()).isEqualTo(refundTxnId);
        assertThat(stored.getAutoConfirmAt()).hasSameTimeAs(originalAutoConfirmAt);
    }

    @Test
    void mapperUpdateCountZeroShouldMapToSemanticStaleOutcome() {
        MarketOrderMapper zeroUpdateMapper = mock(MarketOrderMapper.class);
        MarketOrderRepository zeroUpdateRepository = new MyBatisMarketOrderRepository(zeroUpdateMapper);
        MarketOrderTransition transition = orderIn(
                MarketOrderStatus.ESCROWED,
                null,
                null,
                null,
                null
        ).markDelivered(new Date(2_000L));

        Object outcome = invokeApply(zeroUpdateRepository, transition);

        assertThat(outcome).hasToString("STALE");
        assertThat(mockingDetails(zeroUpdateMapper).getInvocations())
                .extracting(invocation -> invocation.getMethod().getName())
                .containsExactly("apply");
    }

    private static Object invokeApply(MarketOrderRepository target, MarketOrderTransition transition) {
        Method method = Arrays.stream(MarketOrderRepository.class.getMethods())
                .filter(candidate -> candidate.getName().equals("apply"))
                .filter(candidate -> Arrays.equals(
                        candidate.getParameterTypes(),
                        new Class<?>[]{MarketOrderTransition.class}
                ))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "MarketOrderRepository must expose apply(MarketOrderTransition)"
                ));
        try {
            return method.invoke(target, transition);
        } catch (IllegalAccessException error) {
            throw new AssertionError("MarketOrderRepository.apply must be public", error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error invocationError) {
                throw invocationError;
            }
            throw new AssertionError(cause);
        }
    }

    private static MarketOrder orderIn(
            MarketOrderStatus status,
            UUID escrowTxnId,
            UUID releaseTxnId,
            UUID refundTxnId,
            Date autoConfirmAt
    ) {
        MarketOrder order = instantiateOrder();
        setField(order, "orderId", ORDER_ID);
        setField(order, "requestId", "market:persistence-transition");
        setField(order, "listingId", uuid(302));
        setField(order, "goodsType", MarketGoodsType.PHYSICAL.code());
        setField(order, "sellerUserId", uuid(303));
        setField(order, "buyerUserId", uuid(304));
        setField(order, "quantity", 1);
        setField(order, "unitPriceSnapshot", 1_200L);
        setField(order, "totalAmount", 1_200L);
        setField(order, "deliveryModeSnapshot", MarketDeliveryMode.MANUAL.code());
        setField(order, "listingTitleSnapshot", "Used keyboard");
        setField(order, "status", status.code());
        setField(order, "escrowTxnId", escrowTxnId);
        setField(order, "releaseTxnId", releaseTxnId);
        setField(order, "refundTxnId", refundTxnId);
        setField(order, "autoConfirmAt", autoConfirmAt);
        return order;
    }

    private static MarketOrder instantiateOrder() {
        try {
            Constructor<MarketOrder> constructor = MarketOrder.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("MarketOrder must retain a non-public persistence constructor", error);
        }
    }

    private static void setField(MarketOrder order, String name, Object value) {
        try {
            Field field = MarketOrder.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(order, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot prepare MarketOrder field: " + name, error);
        }
    }
}
