package com.nowcoder.community.im.core.application;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationApplicationServicePaginationOverflowTest {

    @Test
    void listConversationsShouldNotPassNegativeOffsetWhenPageIsHuge() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        List<Object[]> capturedArgs = new ArrayList<>();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    capturedArgs.add(invocation.getArguments());
                    return List.of();
                });

        ConversationApplicationService applicationService = new ConversationApplicationService(
                mock(com.nowcoder.community.im.core.repository.PrivateMessageRepository.class),
                mock(com.nowcoder.community.im.core.repository.ConversationReadStateRepository.class),
                mock(com.nowcoder.community.im.core.repository.ConversationRepository.class),
                jdbcTemplate
        );

        applicationService.listConversations(
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                Integer.MAX_VALUE,
                200
        );

        assertThat(capturedArgs).hasSize(1);
        Object[] args = capturedArgs.get(0);
        assertThat(args).hasSize(7);
        assertThat(args[6]).isInstanceOf(Number.class);
        assertThat(((Number) args[6]).longValue()).isGreaterThanOrEqualTo(0L);
    }
}
