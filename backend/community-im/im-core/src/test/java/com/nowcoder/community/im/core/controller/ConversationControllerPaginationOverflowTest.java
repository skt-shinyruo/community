package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.repository.ConversationRepository;
import com.nowcoder.community.im.core.repository.PrivateMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationControllerPaginationOverflowTest {

    @Test
    void listConversations_should_not_pass_negative_offset_when_page_is_huge() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        List<Object[]> capturedArgs = new ArrayList<>();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    capturedArgs.add(invocation.getArguments());
                    return List.of();
                });

        ConversationController controller = new ConversationController(
                mock(PrivateMessageRepository.class),
                mock(ConversationReadStateRepository.class),
                mock(ConversationRepository.class),
                jdbcTemplate
        );

        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        controller.listConversations(jwt, Integer.MAX_VALUE, 200);

        assertThat(capturedArgs).hasSize(1);
        Object[] args = capturedArgs.get(0);
        assertThat(args).hasSize(7);
        assertThat(args[6]).isInstanceOf(Number.class);
        assertThat(((Number) args[6]).longValue()).isGreaterThanOrEqualTo(0L);
    }
}
