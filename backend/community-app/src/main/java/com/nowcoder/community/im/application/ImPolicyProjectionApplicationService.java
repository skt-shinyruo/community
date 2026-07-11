package com.nowcoder.community.im.application;

import com.nowcoder.community.im.application.command.ProjectBlockRelationCommand;
import com.nowcoder.community.im.application.command.ProjectUserPolicyCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class ImPolicyProjectionApplicationService {

    private final ImPolicyProjectionOutboxPort outboxPort;

    public ImPolicyProjectionApplicationService(ImPolicyProjectionOutboxPort outboxPort) {
        this.outboxPort = Objects.requireNonNull(outboxPort, "outbox port must not be null");
    }

    @Transactional
    public void projectUserPolicy(ProjectUserPolicyCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateSource(command.sourceDomain(), "user", command.sourceEventId(), command.userId(),
                command.occurredAtEpochMillis(), command.version());
        outboxPort.enqueue(new ImPolicyProjectionEvent(
                command.sourceDomain(), command.sourceEventId(), "USER_POLICY",
                command.userId(), null, null,
                command.userExists(), command.suspended(), command.muted(),
                command.muteUntil(), command.banUntil(), command.canSendPrivate(),
                command.occurredAtEpochMillis(), command.version()
        ));
    }

    @Transactional
    public void projectBlockRelation(ProjectBlockRelationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateSource(command.sourceDomain(), "social", command.sourceEventId(), command.blockerUserId(),
                command.occurredAtEpochMillis(), command.version());
        if (command.blockedUserId() == null) {
            throw new IllegalArgumentException("blocked user id must not be null");
        }
        outboxPort.enqueue(new ImPolicyProjectionEvent(
                command.sourceDomain(), command.sourceEventId(), "BLOCK",
                command.blockerUserId(), command.blockedUserId(), command.active(),
                false, false, false, null, null, false,
                command.occurredAtEpochMillis(), command.version()
        ));
    }

    private void validateSource(
            String actualDomain,
            String expectedDomain,
            String sourceEventId,
            Object primaryId,
            long occurredAtEpochMillis,
            long version
    ) {
        if (!expectedDomain.equals(actualDomain)
                || !StringUtils.hasText(sourceEventId)
                || primaryId == null
                || occurredAtEpochMillis <= 0L
                || version <= 0L) {
            throw new IllegalArgumentException("invalid IM policy projection source metadata");
        }
    }
}
