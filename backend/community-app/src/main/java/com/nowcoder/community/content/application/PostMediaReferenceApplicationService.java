package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaReferenceStatus;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostMediaReferenceApplicationService {

    private final PostMediaReferenceTransactionOperations transactions;
    private final PostMediaStoragePort storagePort;
    private final Clock clock;

    @Autowired
    public PostMediaReferenceApplicationService(
            PostMediaReferenceTransactionOperations transactions,
            PostMediaStoragePort storagePort,
            Clock clock
    ) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    PostMediaReferenceApplicationService(
            PostMediaAssetRepository assetRepository,
            PostMediaStoragePort storagePort,
            Clock clock
    ) {
        this(new PostMediaReferenceTransactionOperations(assetRepository), storagePort, clock);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(PostMediaReferenceCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        PostMediaAsset asset = transactions.load(command.assetId());
        if (asset.referenceOperationVersion() != command.operationVersion()) {
            return;
        }

        switch (command.operation()) {
            case BIND -> processBind(command, asset);
            case RELEASE -> processRelease(command, asset);
        }
    }

    private void processBind(PostMediaReferenceCommand command, PostMediaAsset asset) {
        if (asset.referenceStatus() == PostMediaReferenceStatus.BOUND) {
            return;
        }
        if (asset.referenceStatus() != PostMediaReferenceStatus.BIND_PENDING) {
            return;
        }
        UUID postId = Objects.requireNonNull(asset.postId(), "pending media bind postId must not be null");
        UUID requestedReferenceId = Objects.requireNonNull(
                asset.ossReferenceId(),
                "pending media bind referenceId must not be null"
        );

        UUID boundReferenceId = storagePort.bindReference(
                asset,
                postId,
                requestedReferenceId,
                command.actorUserId()
        );
        if (!requestedReferenceId.equals(boundReferenceId)) {
            throw new IllegalStateException("OSS returned a different media referenceId");
        }

        transactions.markBound(
                command.assetId(),
                command.operationVersion(),
                now()
        );
    }

    private void processRelease(PostMediaReferenceCommand command, PostMediaAsset asset) {
        if (asset.referenceStatus() == PostMediaReferenceStatus.RELEASED) {
            return;
        }
        if (asset.referenceStatus() != PostMediaReferenceStatus.RELEASE_PENDING) {
            return;
        }
        Objects.requireNonNull(asset.ossReferenceId(), "pending media release referenceId must not be null");

        storagePort.releaseReference(asset, command.actorUserId());

        transactions.markReleased(
                command.assetId(),
                command.operationVersion(),
                now()
        );
    }

    private Date now() {
        return Date.from(clock.instant());
    }

}
