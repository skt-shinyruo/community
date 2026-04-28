package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.support.TestUuids;
import com.nowcoder.community.user.api.query.UserPendingRegistrationQueryApi;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.infrastructure.persistence.dataobject.UserDataObject;
import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class UserRegistrationApiAdapterIntegrationTest {

    @Autowired
    private UserPendingRegistrationQueryApi userPendingRegistrationQueryApi;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUpKafkaTemplate() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Test
    void expiredPendingLookupShouldCommitCleanupBeforeThrowingNotFound() {
        UUID userId = TestUuids.uuid(901);
        insertExpiredPendingUser(userId, "pending-901");

        assertThatThrownBy(() -> userPendingRegistrationQueryApi.getPendingUser(userId, Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));

        assertThat(userMapper.selectById(userId)).isNull();
    }

    private void insertExpiredPendingUser(UUID userId, String username) {
        userMapper.deletePendingUser(userId, 0);

        UserDataObject user = new UserDataObject();
        user.setId(userId);
        user.setUsername(username);
        user.setPassword("encoded");
        user.setSalt("");
        user.setEmail(username + "@example.com");
        user.setType(0);
        user.setStatus(0);
        user.setHeaderUrl("/avatar.png");
        user.setCreateTime(Date.from(Instant.now().minus(Duration.ofMinutes(31))));
        userMapper.insertUser(user);
    }
}
