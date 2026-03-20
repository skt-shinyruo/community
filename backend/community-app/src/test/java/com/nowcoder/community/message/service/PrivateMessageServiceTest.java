package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.message.mapper.MessageMapper;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.security.OwnerGuard;
import com.nowcoder.community.social.block.BlockService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.annotation.MapperScan;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = PrivateMessageServiceTest.MapperOnlyTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class PrivateMessageServiceTest {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearMessages() {
        jdbcTemplate.update("delete from message");
    }

    @Test
    void sendShouldRejectUnknownPositiveRecipientBeforePersisting() {
        MessageMapper mapper = mock(MessageMapper.class);
        UserLookupService userLookupService = mock(UserLookupService.class);
        BlockService blockService = mock(BlockService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);

        when(userLookupService.safeGetUser(404)).thenReturn(null);

        PrivateMessageService service = new PrivateMessageService(
                mapper,
                userLookupService,
                blockService,
                moderationGuard,
                ownerGuard()
        );

        BusinessException ex = catchThrowableOfType(
                () -> service.send(7, 404, "hello"),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).isEqualTo("目标用户不存在");
        verifyNoInteractions(mapper, blockService);
    }

    @Test
    void sendShouldRejectSelfMessagesBeforePersistingConversationId() {
        MessageMapper mapper = mock(MessageMapper.class);
        UserLookupService userLookupService = mock(UserLookupService.class);
        BlockService blockService = mock(BlockService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);

        PrivateMessageService service = new PrivateMessageService(
                mapper,
                userLookupService,
                blockService,
                moderationGuard,
                ownerGuard()
        );

        BusinessException ex = catchThrowableOfType(
                () -> service.send(7, 7, "hello"),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("不能给自己发送私信");
        verifyNoInteractions(mapper, userLookupService, blockService);
    }

    @Test
    void listLettersShouldKeepValidTwoPartyConversationReadableWhenRealUserOneParticipates() {
        jdbcTemplate.update(
                "insert into message (from_id, to_id, conversation_id, content, status, create_time) values (?, ?, ?, ?, ?, current_timestamp)",
                1, 2, "1_2", "hello from real user one", NoticeService.STATUS_UNREAD
        );

        PrivateMessageService service = new PrivateMessageService(
                messageMapper,
                mock(UserLookupService.class),
                null,
                mock(UserModerationGuard.class),
                ownerGuard()
        );

        List<Message> letters = service.listLetters(2, "1_2", 0, 10);

        assertThat(letters).extracting(Message::getFromId, Message::getToId, Message::getConversationId)
                .containsExactly(org.assertj.core.api.Assertions.tuple(1, 2, "1_2"));
    }

    private OwnerGuard ownerGuard() {
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
        return new OwnerGuard(meterRegistryProvider);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(
            annotationClass = Mapper.class,
            basePackages = "com.nowcoder.community.message.mapper"
    )
    static class MapperOnlyTestConfig {
    }
}
