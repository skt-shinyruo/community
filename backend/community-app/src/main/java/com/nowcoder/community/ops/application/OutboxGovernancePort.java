package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxGovernancePort {

    List<OutboxBacklogResult> listBacklog();

    List<OutboxEventResult> findEvents(FindOutboxEventsCommand command);

    Optional<OutboxEventResult> findById(UUID id);

    boolean requeueDead(UUID id, String reason);
}
