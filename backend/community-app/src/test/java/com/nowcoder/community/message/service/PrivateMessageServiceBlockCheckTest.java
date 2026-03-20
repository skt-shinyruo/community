package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.message.mapper.MessageMapper;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.security.OwnerGuard;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.user.dto.UserSummary;
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
        UserLookupService userLookupService = mock(UserLookupService.class);
        BlockService blockService = mock(BlockService.class);
        UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
        OwnerGuard ownerGuard = mock(OwnerGuard.class);

        UserSummary target = new UserSummary();
        target.setId(2);
        when(userLookupService.safeGetUser(2)).thenReturn(target);
        when(blockService.isEitherBlocked(1, 2)).thenReturn(true);

        PrivateMessageService service = new PrivateMessageService(
                messageMapper,
                userLookupService,
                blockService,
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
