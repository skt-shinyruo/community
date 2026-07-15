package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaUploadStatus;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class PostMediaUploadRecoveryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PostMediaUploadRecoveryApplicationService.class);

    private final PostMediaAssetRepository assetRepository;
    private final PostMediaStoragePort storagePort;
    private final PostMediaUploadTransactionOperations transactionOperations;
    private final Clock clock;

    @Autowired
    public PostMediaUploadRecoveryApplicationService(
            PostMediaAssetRepository assetRepository,
            PostMediaStoragePort storagePort,
            PostMediaUploadTransactionOperations transactionOperations,
            Clock clock
    ) {
        this.assetRepository = Objects.requireNonNull(assetRepository, "assetRepository must not be null");
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort must not be null");
        this.transactionOperations = Objects.requireNonNull(
                transactionOperations, "transactionOperations must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public PostMediaUploadRecoveryApplicationService(
            PostMediaAssetRepository assetRepository,
            PostMediaStoragePort storagePort
    ) {
        this(
                assetRepository,
                storagePort,
                new PostMediaUploadTransactionOperations(assetRepository),
                Clock.systemUTC()
        );
    }

    public void recoverStaleCompleting(Date updatedBefore, int limit) {
        if (updatedBefore == null) {
            return;
        }
        List<PostMediaAsset> assets = assetRepository.listStaleCompleting(updatedBefore, limit);
        if (assets == null || assets.isEmpty()) {
            return;
        }
        for (PostMediaAsset asset : assets) {
            try {
                recover(asset, updatedBefore);
            } catch (RuntimeException exception) {
                recordRecoveryFailure(asset, updatedBefore, exception);
                log.warn(
                        "[content-media-upload] failed to recover assetId={}: {}",
                        asset == null ? null : asset.id(),
                        exception.toString()
                );
            }
        }
    }

    private void recordRecoveryFailure(PostMediaAsset asset, Date staleBefore, RuntimeException failure) {
        if (asset == null) {
            return;
        }
        try {
            transactionOperations.recordRecoveryFailure(
                    asset.id(),
                    asset.uploadOperationVersion(),
                    staleBefore,
                    describeRecoveryFailure(failure),
                    Date.from(clock.instant())
            );
        } catch (RuntimeException recordFailure) {
            log.warn(
                    "[content-media-upload] failed to defer recovery assetId={}: {}",
                    asset.id(),
                    recordFailure.toString()
            );
        }
    }

    private static String describeRecoveryFailure(RuntimeException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        return "RECOVERY_FAILED:" + failure.getClass().getSimpleName() + ":" + message
                .replace('\n', ' ')
                .replace('\r', ' ');
    }

    private void recover(PostMediaAsset asset, Date staleBefore) {
        if (asset == null) {
            return;
        }
        Date now = Date.from(clock.instant());
        if (asset.uploadStatus() == PostMediaUploadStatus.OBJECT_COMPLETED) {
            transactionOperations.markCompleted(asset.id(), asset.uploadOperationVersion(), now);
            return;
        }
        if (asset.uploadStatus() != PostMediaUploadStatus.COMPLETING) {
            return;
        }
        PostMediaStoragePort.CanonicalPostMedia canonical = storagePort.queryCanonicalMetadata(asset);
        if (canonical == null || canonical.outcome() == PostMediaStoragePort.CanonicalMetadataOutcome.UNKNOWN) {
            transactionOperations.resetStaleCompletion(
                    asset.id(), asset.uploadOperationVersion(), staleBefore, now);
            return;
        }
        if (canonical.outcome() == PostMediaStoragePort.CanonicalMetadataOutcome.NOT_FOUND) {
            transactionOperations.markFailed(
                    asset.id(), asset.uploadOperationVersion(), "OSS object not found", now);
            return;
        }
        boolean metadataStored = transactionOperations.markObjectCompleted(
                asset.id(),
                asset.uploadOperationVersion(),
                canonical.versionId(),
                canonical.publicUrl(),
                canonical.contentType(),
                canonical.contentLength(),
                now
        );
        if (metadataStored) {
            transactionOperations.markCompleted(asset.id(), asset.uploadOperationVersion(), now);
        }
    }
}
