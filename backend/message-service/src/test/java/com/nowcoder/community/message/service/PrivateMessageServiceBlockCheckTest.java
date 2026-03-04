package com.nowcoder.community.message.service;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.message.dao.MessageMapper;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.security.OwnerGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivateMessageServiceBlockCheckTest {

    @Test
    void sendShouldRejectWhenEitherBlocked() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        SocialBlockClient socialBlockClient = mock(SocialBlockClient.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        OwnerGuard ownerGuard = mock(OwnerGuard.class);

        when(socialBlockClient.isEitherBlocked(1, 2)).thenReturn(true);

        PrivateMessageService service = new PrivateMessageService(
                messageMapper,
                userServiceClient,
                socialBlockClient,
                moderationGuard,
                ownerGuard
        );

        assertThatThrownBy(() -> service.send(1, 2, "hi"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN);
                });

        verify(messageMapper, never()).insertMessage(ArgumentMatchers.any(Message.class));
    }
}
