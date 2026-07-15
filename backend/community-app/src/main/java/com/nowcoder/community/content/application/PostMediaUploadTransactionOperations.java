package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Component
public class PostMediaUploadTransactionOperations {

    private final PostMediaAssetRepository assetRepository;

    public PostMediaUploadTransactionOperations(PostMediaAssetRepository assetRepository) {
        this.assetRepository = Objects.requireNonNull(assetRepository, "assetRepository must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID createDraft(PostMediaAsset asset) {
        return assetRepository.createDraft(asset);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PostMediaAsset load(UUID assetId) {
        return assetRepository.getRequired(assetId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimCompletion(UUID assetId, UUID actorUserId, long operationVersion, Date updateTime) {
        return assetRepository.claimUploadCompletion(assetId, actorUserId, operationVersion, updateTime);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markObjectCompleted(
            UUID assetId,
            long operationVersion,
            UUID versionId,
            String publicUrl,
            String contentType,
            long contentLength,
            Date updateTime
    ) {
        return assetRepository.markObjectCompleted(
                assetId, operationVersion, versionId, publicUrl, contentType, contentLength, updateTime);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markCompleted(UUID assetId, long operationVersion, Date updateTime) {
        return assetRepository.markUploadCompleted(assetId, operationVersion, updateTime);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(
            UUID assetId,
            long operationVersion,
            String failureReason,
            Date updateTime
    ) {
        return assetRepository.markUploadFailed(assetId, operationVersion, failureReason, updateTime);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean resetStaleCompletion(
            UUID assetId,
            long operationVersion,
            Date staleBefore,
            Date resetAt
    ) {
        return assetRepository.resetStaleUploadCompletion(
                assetId, operationVersion, staleBefore, resetAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordRecoveryFailure(
            UUID assetId,
            long operationVersion,
            Date staleBefore,
            String failureReason,
            Date updateTime
    ) {
        return assetRepository.recordUploadRecoveryFailure(
                assetId, operationVersion, staleBefore, failureReason, updateTime);
    }
}
