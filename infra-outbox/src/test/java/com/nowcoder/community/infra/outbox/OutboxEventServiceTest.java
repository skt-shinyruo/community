package com.nowcoder.community.infra.outbox;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventServiceTest {

    @Test
    void recoverStuckSendingShouldMoveToRetry() {
        OutboxEventMapper outboxEventMapper = mock(OutboxEventMapper.class);
        OutboxProperties properties = new OutboxProperties();
        OutboxEventService outboxEventService = new OutboxEventService(outboxEventMapper, properties);

        List<Long> ids = List.of(1L);
        when(outboxEventMapper.selectStuckSendingIds(any(Date.class), eq(1000))).thenReturn(ids);
        when(outboxEventMapper.markRetrySendingByIds(eq(ids), any(Date.class), eq("stuck SENDING recovered"))).thenReturn(1);

        long beforeCallMs = System.currentTimeMillis();
        int recovered = outboxEventService.recoverStuckSending(10, 50000);
        long afterCallMs = System.currentTimeMillis();

        assertThat(recovered).isEqualTo(1);

        ArgumentCaptor<Date> beforeCaptor = ArgumentCaptor.forClass(Date.class);
        verify(outboxEventMapper).selectStuckSendingIds(beforeCaptor.capture(), eq(1000));

        long beforeMs = beforeCaptor.getValue().getTime();
        assertThat(beforeMs).isBetween(beforeCallMs - 1000 - 2000, afterCallMs - 1000 + 2000);

        verify(outboxEventMapper).markRetrySendingByIds(eq(ids), any(Date.class), eq("stuck SENDING recovered"));
    }

    @Test
    void cleanupSentShouldDeleteByRetentionAndLimit() {
        OutboxEventMapper outboxEventMapper = mock(OutboxEventMapper.class);
        OutboxProperties properties = new OutboxProperties();
        OutboxEventService outboxEventService = new OutboxEventService(outboxEventMapper, properties);

        when(outboxEventMapper.deleteSentBefore(any(Date.class), eq(1))).thenReturn(1);

        long beforeCallMs = System.currentTimeMillis();
        int deleted = outboxEventService.cleanupSent(0, 0);
        long afterCallMs = System.currentTimeMillis();

        assertThat(deleted).isEqualTo(1);

        ArgumentCaptor<Date> beforeCaptor = ArgumentCaptor.forClass(Date.class);
        verify(outboxEventMapper).deleteSentBefore(beforeCaptor.capture(), eq(1));

        long dayMs = 24L * 3600 * 1000;
        long beforeMs = beforeCaptor.getValue().getTime();
        assertThat(beforeMs).isBetween(beforeCallMs - dayMs - 5000, afterCallMs - dayMs + 5000);
    }
}

