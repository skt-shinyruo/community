package com.nowcoder.community.user.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.support.TestUuids;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class UserRegistrationServiceIntegrationTest {

    @Autowired
    private UserRegistrationService userRegistrationService;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void expiredPendingLookupShouldCommitCleanupBeforeThrowingNotFound() {
        UUID userId = TestUuids.uuid(901);
        insertExpiredPendingUser(userId, "pending-901");

        assertThatThrownBy(() -> userRegistrationService.getPendingRegistrationUser(userId, Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));

        assertThat(userMapper.selectById(userId)).isNull();
    }

    private void insertExpiredPendingUser(UUID userId, String username) {
        userMapper.deletePendingUser(userId, 0);

        User user = new User();
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
