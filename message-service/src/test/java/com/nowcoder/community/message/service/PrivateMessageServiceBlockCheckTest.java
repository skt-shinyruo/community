package com.nowcoder.community.message.service;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.security.OwnerGuard;
import com.nowcoder.community.message.dao.MessageMapper;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.projection.UserModerationProjectionRepository;
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
    void sendShouldFailClosedWhenProjectionUnknownAndSaysBlocked() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserServiceClient userServiceClient = mock(UserServiceClient.class);
        SocialServiceClient socialServiceClient = mock(SocialServiceClient.class);
        UserModerationProjectionRepository projectionRepository = mock(UserModerationProjectionRepository.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        OwnerGuard ownerGuard = mock(OwnerGuard.class);

        when(projectionRepository.checkEitherBlocked(1, 2)).thenReturn(UserModerationProjectionRepository.BlockCheck.UNKNOWN);
        when(socialServiceClient.isEitherBlocked(1, 2)).thenReturn(true);

        PrivateMessageService service = new PrivateMessageService(
                messageMapper,
                userServiceClient,
                socialServiceClient,
                projectionRepository,
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
        verify(projectionRepository).upsertBlockRelation(
                ArgumentMatchers.eq(1),
                ArgumentMatchers.eq(2),
                ArgumentMatchers.eq(true),
                ArgumentMatchers.any(java.time.Instant.class)
        );
        verify(projectionRepository).upsertBlockRelation(
                ArgumentMatchers.eq(2),
                ArgumentMatchers.eq(1),
                ArgumentMatchers.eq(true),
                ArgumentMatchers.any(java.time.Instant.class)
        );
    }
}
