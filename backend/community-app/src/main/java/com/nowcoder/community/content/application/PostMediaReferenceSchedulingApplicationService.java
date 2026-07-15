package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;
import com.nowcoder.community.content.domain.model.PostMediaReferenceStatus;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostMediaReferenceSchedulingApplicationService {

    private final PostMediaAssetRepository assetRepository;
    private final PostMediaReferenceCommandPublisher commandPublisher;
    private final Clock clock;

    public PostMediaReferenceSchedulingApplicationService(
            PostMediaAssetRepository assetRepository,
            PostMediaReferenceCommandPublisher commandPublisher,
            Clock clock
    ) {
        this.assetRepository = Objects.requireNonNull(assetRepository, "assetRepository must not be null");
        this.commandPublisher = Objects.requireNonNull(commandPublisher, "commandPublisher must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleReleaseForDeletedPost(UUID postId) {
        Objects.requireNonNull(postId, "postId must not be null");
        List<PostMediaAsset> assets = assetRepository.listByPostId(postId);
        Date now = Date.from(clock.instant());
        for (PostMediaAsset asset : assets) {
            if (asset.referenceStatus() == PostMediaReferenceStatus.RELEASE_PENDING) {
                publishRelease(asset, asset.referenceOperationVersion());
            } else if (asset.referenceStatus() == PostMediaReferenceStatus.BOUND
                    || asset.referenceStatus() == PostMediaReferenceStatus.BIND_PENDING) {
                publishRelease(asset, assetRepository.requestRelease(asset.id(), now));
            }
        }
    }

    private void publishRelease(PostMediaAsset asset, long operationVersion) {
        commandPublisher.publish(new PostMediaReferenceCommand(
                asset.id(),
                PostMediaReferenceOperation.RELEASE,
                operationVersion,
                asset.ownerUserId()
        ));
    }
}
