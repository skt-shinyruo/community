package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.mapper.MessageMapper;
import com.nowcoder.community.message.security.OwnerGuard;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.service.UserQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void serviceShouldOnlyExposeExplicitGovernanceConstructor() {
        assertThat(PrivateMessageService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        MessageMapper.class,
                        MessageUserQueryService.class,
                        PrivateMessageGovernanceService.class,
                        OwnerGuard.class
                ));
    }

    @Test
    void sendShouldRejectUnknownPositiveRecipientBeforePersisting() {
        MessageMapper mapper = mock(MessageMapper.class);
        PrivateMessageGovernanceService governanceService = mock(PrivateMessageGovernanceService.class);
        doThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND, "目标用户不存在"))
                .when(governanceService)
                .validateCanSendPrivateMessage(7, 404);

        PrivateMessageService service = new PrivateMessageService(
                mapper,
                mock(MessageUserQueryService.class),
                governanceService,
                ownerGuard()
        );

        BusinessException ex = catchThrowableOfType(
                () -> service.send(7, 404, "hello"),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).isEqualTo("目标用户不存在");
        verify(governanceService).validateCanSendPrivateMessage(7, 404);
        verifyNoInteractions(mapper);
    }

    @Test
    void sendShouldRejectSelfMessagesBeforePersistingConversationId() {
        MessageMapper mapper = mock(MessageMapper.class);
        PrivateMessageGovernanceService governanceService = mock(PrivateMessageGovernanceService.class);
        doThrow(new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "不能给自己发送私信"))
                .when(governanceService)
                .validateCanSendPrivateMessage(7, 7);

        PrivateMessageService service = new PrivateMessageService(
                mapper,
                mock(MessageUserQueryService.class),
                governanceService,
                ownerGuard()
        );

        BusinessException ex = catchThrowableOfType(
                () -> service.send(7, 7, "hello"),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("不能给自己发送私信");
        verify(governanceService).validateCanSendPrivateMessage(7, 7);
        verifyNoInteractions(mapper);
    }

    @Test
    void sendShouldPersistMessageAfterGovernanceValidation() {
        MessageMapper mapper = mock(MessageMapper.class);
        PrivateMessageGovernanceService governanceService = mock(PrivateMessageGovernanceService.class);

        PrivateMessageService service = new PrivateMessageService(
                mapper,
                mock(MessageUserQueryService.class),
                governanceService,
                ownerGuard()
        );

        service.send(7, 9, "hello");

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(governanceService).validateCanSendPrivateMessage(7, 9);
        verify(mapper).insertMessage(messageCaptor.capture());

        Message persisted = messageCaptor.getValue();
        assertThat(persisted.getFromId()).isEqualTo(7);
        assertThat(persisted.getToId()).isEqualTo(9);
        assertThat(persisted.getConversationId()).isEqualTo("7_9");
        assertThat(persisted.getContent()).isEqualTo("hello");
        assertThat(persisted.getStatus()).isEqualTo(NoticeService.STATUS_UNREAD);
        assertThat(persisted.getCreateTime()).isNotNull();
    }

    @Test
    void sendShouldRejectInvalidSenderUsingSharedGovernanceContract() {
        MessageMapper mapper = mock(MessageMapper.class);
        UserQueryService userQueryService = mock(UserQueryService.class);
        BlockService blockService = mock(BlockService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        PrivateMessageGovernanceService governanceService =
                new PrivateMessageGovernanceService(userQueryService, moderationGuard, blockService);

        PrivateMessageService service = new PrivateMessageService(
                mapper,
                mock(MessageUserQueryService.class),
                governanceService,
                ownerGuard()
        );

        BusinessException ex = catchThrowableOfType(
                () -> service.send(0, 2, "hello"),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("fromUserId 非法");
        verifyNoInteractions(mapper, userQueryService, blockService, moderationGuard);
    }

    @Test
    void sendShouldReuseSharedInvalidRecipientSemantics() {
        MessageMapper mapper = mock(MessageMapper.class);
        UserQueryService userQueryService = mock(UserQueryService.class);
        BlockService blockService = mock(BlockService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        PrivateMessageGovernanceService governanceService =
                new PrivateMessageGovernanceService(userQueryService, moderationGuard, blockService);

        PrivateMessageService service = new PrivateMessageService(
                mapper,
                mock(MessageUserQueryService.class),
                governanceService,
                ownerGuard()
        );

        BusinessException ex = catchThrowableOfType(
                () -> service.send(7, 0, "hello"),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("toUserId 非法");
        verifyNoInteractions(mapper, userQueryService, blockService, moderationGuard);
    }

    @Test
    void listLettersShouldKeepValidTwoPartyConversationReadableWhenRealUserOneParticipates() {
        jdbcTemplate.update(
                "insert into message (from_id, to_id, conversation_id, content, status, create_time) values (?, ?, ?, ?, ?, current_timestamp)",
                1, 2, "1_2", "hello from real user one", NoticeService.STATUS_UNREAD
        );

        PrivateMessageService service = new PrivateMessageService(
                messageMapper,
                mock(MessageUserQueryService.class),
                mock(PrivateMessageGovernanceService.class),
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
