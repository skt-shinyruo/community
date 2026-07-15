package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.PostMediaReferenceSchedulingApplicationService;
import com.nowcoder.community.content.domain.event.PostDeletedDomainEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostDeletedMediaReferenceBridgeTest {

    @Test
    void bridgeShouldScheduleMediaReleaseBeforeTheDeletionTransactionCommits() throws Exception {
        PostMediaReferenceSchedulingApplicationService applicationService =
                mock(PostMediaReferenceSchedulingApplicationService.class);
        PostDeletedMediaReferenceBridge bridge = new PostDeletedMediaReferenceBridge(applicationService);
        Method listener = PostDeletedMediaReferenceBridge.class
                .getDeclaredMethod("onPostDeleted", PostDeletedDomainEvent.class);
        TransactionalEventListener annotation = listener.getAnnotation(TransactionalEventListener.class);

        bridge.onPostDeleted(new PostDeletedDomainEvent(uuid(501)));

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();
        verify(applicationService).scheduleReleaseForDeletedPost(uuid(501));
    }

    @Test
    void inboundBridgeShouldHoldOnlyItsSameDomainApplicationService() {
        List<Class<?>> collaboratorTypes = Arrays.stream(PostDeletedMediaReferenceBridge.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .toList();

        assertThat(collaboratorTypes).containsExactly(PostMediaReferenceSchedulingApplicationService.class);
    }
}
