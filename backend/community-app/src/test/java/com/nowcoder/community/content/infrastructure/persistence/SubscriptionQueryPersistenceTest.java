package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.application.SubscriptionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class SubscriptionQueryPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SubscriptionQuery subscriptionQuery;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_subscription_category");
    }

    @Test
    void queryShouldReturnSubscribedCategoryIdsInPersistenceOrder() {
        UUID userId = uuid(721);
        UUID olderCategoryId = uuid(722);
        UUID newerCategoryId = uuid(723);
        insert(userId, olderCategoryId, Instant.parse("2026-07-08T00:00:00Z"));
        insert(userId, newerCategoryId, Instant.parse("2026-07-09T00:00:00Z"));
        insert(uuid(724), uuid(725), Instant.parse("2026-07-10T00:00:00Z"));

        assertThat(subscriptionQuery.listSubscribedCategoryIds(userId))
                .containsExactly(newerCategoryId, olderCategoryId);
    }

    @Test
    void queryShouldPreserveInvalidUserFailure() {
        assertThatThrownBy(() -> subscriptionQuery.listSubscribedCategoryIds(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("userId 非法");
    }

    private void insert(UUID userId, UUID categoryId, Instant createdAt) {
        jdbcTemplate.update(
                "insert into user_subscription_category(user_id, category_id, create_time) values (?, ?, ?)",
                BinaryUuidCodec.toBytes(userId),
                BinaryUuidCodec.toBytes(categoryId),
                Timestamp.from(createdAt)
        );
    }
}
