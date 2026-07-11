package com.nowcoder.community.im.application;

import com.nowcoder.community.im.application.command.ProjectBlockRelationCommand;
import com.nowcoder.community.im.application.command.ProjectUserPolicyCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImPolicyProjectionApplicationServiceTest {

    private final ImPolicyProjectionOutboxPort outboxPort = mock(ImPolicyProjectionOutboxPort.class);
    private final ImPolicyProjectionApplicationService service =
            new ImPolicyProjectionApplicationService(outboxPort);

    @Test
    void projectUserPolicyShouldBuildCanonicalOutboxEvent() {
        ProjectUserPolicyCommand command = new ProjectUserPolicyCommand(
                "user", "user-event-1", uuid(7), true, false, true,
                1712345678901L, null, false, 1712345678901L, 777L
        );

        service.projectUserPolicy(command);

        ArgumentCaptor<ImPolicyProjectionEvent> captor = ArgumentCaptor.forClass(ImPolicyProjectionEvent.class);
        verify(outboxPort).enqueue(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ImPolicyProjectionEvent(
                "user", "user-event-1", "USER_POLICY", uuid(7), null, null,
                true, false, true, 1712345678901L, null, false,
                1712345678901L, 777L
        ));
    }

    @Test
    void projectBlockRelationShouldBuildCanonicalOutboxEvent() {
        service.projectBlockRelation(new ProjectBlockRelationCommand(
                "social", "social-event-1", uuid(11), uuid(22), true,
                1712345678901L, 888L
        ));

        ArgumentCaptor<ImPolicyProjectionEvent> captor = ArgumentCaptor.forClass(ImPolicyProjectionEvent.class);
        verify(outboxPort).enqueue(captor.capture());
        ImPolicyProjectionEvent event = captor.getValue();
        assertThat(event.sourceDomain()).isEqualTo("social");
        assertThat(event.sourceEventId()).isEqualTo("social-event-1");
        assertThat(event.kind()).isEqualTo("BLOCK");
        assertThat(event.primaryUserId()).isEqualTo(uuid(11));
        assertThat(event.secondaryUserId()).isEqualTo(uuid(22));
        assertThat(event.active()).isTrue();
        assertThat(event.occurredAtEpochMillis()).isEqualTo(1712345678901L);
        assertThat(event.version()).isEqualTo(888L);
    }

    @Test
    void projectionCommandsShouldRejectWrongDomainOrInvalidMetadata() {
        assertThatThrownBy(() -> service.projectUserPolicy(new ProjectUserPolicyCommand(
                "social", "evt", uuid(7), true, false, false,
                null, null, true, 1L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.projectBlockRelation(new ProjectBlockRelationCommand(
                "social", " ", uuid(11), uuid(22), true, 0L, 0L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
