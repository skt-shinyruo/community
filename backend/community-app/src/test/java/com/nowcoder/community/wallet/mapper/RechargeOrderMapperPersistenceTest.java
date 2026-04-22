package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.entity.RechargeOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class RechargeOrderMapperPersistenceTest {

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-7000-8000-000000000621");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RechargeOrderMapper rechargeOrderMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from recharge_order");
    }

    @Test
    void rechargeOrderPrimaryKeyShouldRoundTripAsApplicationAssignedUuid() throws Exception {
        int inserted = jdbcTemplate.update(
                "insert into recharge_order(order_id, request_id, user_id, amount, status, create_time, update_time) values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)",
                BinaryUuidCodec.toBytes(ORDER_ID),
                "recharge:req-persist-1",
                101L,
                1200L,
                "PAID"
        );

        assertThat(inserted).isEqualTo(1);

        byte[] storedOrderId = jdbcTemplate.queryForObject(
                "select order_id from recharge_order where request_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "recharge:req-persist-1"
        );
        assertThat(storedOrderId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedOrderId)).isEqualTo(ORDER_ID);

        RechargeOrder order = rechargeOrderMapper.selectByRequestId("recharge:req-persist-1");
        assertThat(order).isNotNull();

        Method getter = RechargeOrder.class.getMethod("getOrderId");
        Object orderId = getter.invoke(order);
        assertThat(orderId).isInstanceOf(UUID.class);
        assertThat(orderId).isEqualTo(ORDER_ID);
    }
}
