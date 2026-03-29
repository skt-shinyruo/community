package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.mapper.MessageMapper;
import com.nowcoder.community.message.security.OwnerGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PrivateMessageServiceBlockCheckTest {

    @Test
    void sendShouldRejectWhenEitherBlocked() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        PrivateMessageGovernanceService governanceService = mock(PrivateMessageGovernanceService.class);
        OwnerGuard ownerGuard = mock(OwnerGuard.class);
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN, "双方存在拉黑关系，无法发送私信"))
                .when(governanceService)
                .validateCanSendPrivateMessage(1, 2);

        PrivateMessageService service = new PrivateMessageService(
                messageMapper,
                mock(MessageUserQueryService.class),
                governanceService,
                ownerGuard,
                new MessageItemAssembler()
        );

        assertThatThrownBy(() -> service.send(1, 2, "hi"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN);
                });

        verify(governanceService).validateCanSendPrivateMessage(1, 2);
        verify(messageMapper, never()).insertMessage(ArgumentMatchers.any(Message.class));
    }
}
