package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.PostMediaReferenceQueryPort.RemoteReferenceStatus;
import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.application.command.ReconcilePostMediaReferencesCommand;
import com.nowcoder.community.content.application.result.PostMediaReferenceReconciliationResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;
import com.nowcoder.community.content.domain.model.PostMediaReferenceStatus;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.domain.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostMediaReferenceReconciliationApplicationService {

    private final PostMediaAssetRepository assetRepository;
    private final PostRepository postRepository;
    private final PostMediaReferenceQueryPort queryPort;
    private final PostMediaReferenceCommandPublisher commandPublisher;
    private final PostMediaReferenceMetrics metrics;
    private final Clock clock;

    public PostMediaReferenceReconciliationApplicationService(
            PostMediaAssetRepository assetRepository,
            PostRepository postRepository,
            PostMediaReferenceQueryPort queryPort,
            PostMediaReferenceCommandPublisher commandPublisher,
            PostMediaReferenceMetrics metrics,
            Clock clock
    ) {
        this.assetRepository = Objects.requireNonNull(assetRepository, "assetRepository must not be null");
        this.postRepository = Objects.requireNonNull(postRepository, "postRepository must not be null");
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.commandPublisher = Objects.requireNonNull(commandPublisher, "commandPublisher must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public PostMediaReferenceReconciliationResult reconcile(ReconcilePostMediaReferencesCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        List<PostMediaAsset> assets = assetRepository.scanReferenceStatesAfter(
                command.afterAssetId(),
                command.batchSize()
        );
        int pending = 0;
        int drifted = 0;
        int scheduled = 0;
        int failed = 0;

        for (PostMediaAsset asset : assets) {
            boolean isPending = asset.referenceStatus() == PostMediaReferenceStatus.BIND_PENDING
                    || asset.referenceStatus() == PostMediaReferenceStatus.RELEASE_PENDING;
            if (isPending) {
                pending++;
            }
            try {
                ReconciliationOutcome outcome = reconcileAsset(asset);
                drifted += outcome.drifted();
                scheduled += outcome.scheduled();
            } catch (RuntimeException exception) {
                failed++;
            }
        }

        metrics.setPendingReferences(pending);
        metrics.setDriftedReferences(drifted);
        UUID next = assets.isEmpty()
                ? command.afterAssetId()
                : assets.get(assets.size() - 1).id();
        return new PostMediaReferenceReconciliationResult(
                next,
                assets.size() >= command.batchSize(),
                assets.size(),
                pending,
                drifted,
                scheduled,
                failed
        );
    }

    private ReconciliationOutcome reconcileAsset(PostMediaAsset asset) {
        return switch (asset.referenceStatus()) {
            case BIND_PENDING -> republish(asset, PostMediaReferenceOperation.BIND, "pending_command");
            case RELEASE_PENDING -> republish(asset, PostMediaReferenceOperation.RELEASE, "pending_command");
            case BOUND -> reconcileBound(asset);
            case RELEASED -> reconcileReleased(asset);
            case UNBOUND -> ReconciliationOutcome.NONE;
        };
    }

    private ReconciliationOutcome reconcileBound(PostMediaAsset asset) {
        PostSnapshot post = postRepository.getRequiredSnapshot(asset.postId());
        if (post.status() == 2) {
            long version = assetRepository.requestRelease(asset.id(), now());
            publish(asset, PostMediaReferenceOperation.RELEASE, version);
            metrics.recordReconciliation("deleted_post", "scheduled");
            return ReconciliationOutcome.DRIFT_SCHEDULED;
        }
        RemoteReferenceStatus remoteStatus = queryRemote(asset);
        if (remoteStatus != RemoteReferenceStatus.MISSING) {
            return ReconciliationOutcome.NONE;
        }
        long version = assetRepository.requestBindRepair(asset.id(), now());
        publish(asset, PostMediaReferenceOperation.BIND, version);
        metrics.recordReconciliation("remote_missing", "scheduled");
        return ReconciliationOutcome.DRIFT_SCHEDULED;
    }

    private ReconciliationOutcome reconcileReleased(PostMediaAsset asset) {
        if (queryRemote(asset) != RemoteReferenceStatus.ACTIVE) {
            return ReconciliationOutcome.NONE;
        }
        long version = assetRepository.requestReleaseRepair(asset.id(), now());
        publish(asset, PostMediaReferenceOperation.RELEASE, version);
        metrics.recordReconciliation("remote_active", "scheduled");
        return ReconciliationOutcome.DRIFT_SCHEDULED;
    }

    private ReconciliationOutcome republish(
            PostMediaAsset asset,
            PostMediaReferenceOperation operation,
            String reason
    ) {
        publish(asset, operation, asset.referenceOperationVersion());
        metrics.recordReconciliation(reason, "scheduled");
        return ReconciliationOutcome.SCHEDULED;
    }

    private RemoteReferenceStatus queryRemote(PostMediaAsset asset) {
        try {
            return queryPort.findReferenceStatus(asset.ossObjectId(), asset.ossReferenceId());
        } catch (RuntimeException exception) {
            metrics.recordReconciliation("remote_query", "failed");
            throw exception;
        }
    }

    private void publish(PostMediaAsset asset, PostMediaReferenceOperation operation, long version) {
        commandPublisher.publish(new PostMediaReferenceCommand(
                asset.id(),
                operation,
                version,
                asset.ownerUserId()
        ));
    }

    private Date now() {
        return Date.from(clock.instant());
    }

    private record ReconciliationOutcome(int drifted, int scheduled) {

        private static final ReconciliationOutcome NONE = new ReconciliationOutcome(0, 0);
        private static final ReconciliationOutcome SCHEDULED = new ReconciliationOutcome(0, 1);
        private static final ReconciliationOutcome DRIFT_SCHEDULED = new ReconciliationOutcome(1, 1);
    }
}
