package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.content.application.command.CreatePostCommand;
import com.nowcoder.community.content.application.command.PostContentBlockCommand;
import com.nowcoder.community.content.application.result.PostCreateResult;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.CategoryRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.repository.PostTagRepository;
import com.nowcoder.community.content.domain.service.PostContentBlockPolicy;
import com.nowcoder.community.content.domain.service.PostPublishingDomainService;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.infra.idempotency.RequestFingerprint;
import com.nowcoder.community.social.api.action.SocialLikeCleanupActionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class PostPublishingApplicationService {

    private static final String CREATE_POST_IDEMPOTENCY_SCOPE = "content:create_post";

    private final ContentSanitizer sensitiveFilter;
    private final IdempotencyGuard idempotencyGuard;
    private final ContentTextCodec textCodec;
    private final PostBusinessEventLogger postBusinessEventLogger;
    private final UserModerationGuard moderationGuard;
    private final PostPublishingDomainService domainService;
    private final PostContentBlockPolicy blockPolicy;
    private final PostRepository postRepository;
    private final PostContentBlockRepository postContentBlockRepository;
    private final PostMediaAssetRepository postMediaAssetRepository;
    private final PostMediaStoragePort postMediaStoragePort;
    private final CategoryRepository categoryRepository;
    private final PostTagRepository postTagRepository;
    private final PostDomainEventPublisher domainEventPublisher;
    private final PostWriteSideEffectScheduler postWriteSideEffectScheduler;
    private final SocialLikeCleanupActionApi socialLikeCleanupActionApi;

    public PostPublishingApplicationService(
            ContentSanitizer sensitiveFilter,
            IdempotencyGuard idempotencyGuard,
            ContentTextCodec textCodec,
            PostBusinessEventLogger postBusinessEventLogger,
            UserModerationGuard moderationGuard,
            PostPublishingDomainService domainService,
            PostContentBlockPolicy blockPolicy,
            PostRepository postRepository,
            PostContentBlockRepository postContentBlockRepository,
            PostMediaAssetRepository postMediaAssetRepository,
            PostMediaStoragePort postMediaStoragePort,
            CategoryRepository categoryRepository,
            PostTagRepository postTagRepository,
            PostDomainEventPublisher domainEventPublisher,
            PostWriteSideEffectScheduler postWriteSideEffectScheduler,
            SocialLikeCleanupActionApi socialLikeCleanupActionApi
    ) {
        this.sensitiveFilter = sensitiveFilter;
        this.idempotencyGuard = idempotencyGuard;
        this.textCodec = textCodec;
        this.postBusinessEventLogger = postBusinessEventLogger;
        this.moderationGuard = moderationGuard;
        this.domainService = domainService;
        this.blockPolicy = blockPolicy;
        this.postRepository = postRepository;
        this.postContentBlockRepository = postContentBlockRepository;
        this.postMediaAssetRepository = postMediaAssetRepository;
        this.postMediaStoragePort = postMediaStoragePort;
        this.categoryRepository = categoryRepository;
        this.postTagRepository = postTagRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.postWriteSideEffectScheduler = postWriteSideEffectScheduler;
        this.socialLikeCleanupActionApi = socialLikeCleanupActionApi;
    }

    @Transactional
    public PostCreateResult create(String idempotencyKey, CreatePostCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID userId = command.userId();
        String requestHash = createPostRequestHash(command);
        return idempotencyGuard.executeRequired(
                CREATE_POST_IDEMPOTENCY_SCOPE,
                userId,
                idempotencyKey,
                requestHash,
                ContentErrorCode.REQUEST_REPLAY_CONFLICT,
                PostCreateResult.class,
                () -> {
            moderationGuard.assertCanSpeak(userId);
            categoryRepository.assertExists(command.categoryId());
            List<PostContentBlockCommand> blocks = sanitizeBlocks(blockPolicy.validateAndNormalize(command.blocks()));
            PostDraft draft = domainService.createDraft(userId, sanitize(command.title()), command.categoryId());
            UUID postId = postRepository.create(draft);
            bindMediaAssets(userId, postId, blocks, new Date());
            postContentBlockRepository.replaceBlocks(postId, toDomainBlocks(postId, blocks));
            postTagRepository.bindTagsToPost(postId, command.tags());
            domainEventPublisher.postPublished(postId);
            postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
            postBusinessEventLogger.postCreate(userId, command.categoryId(), postId);
            return new PostCreateResult(postId);
        });
    }

    @Transactional
    public void updatePost(UUID userId, UUID postId, String title, UUID categoryId, List<String> tags, List<PostContentBlockCommand> blocks) {
        moderationGuard.assertCanSpeak(userId);
        categoryRepository.assertExists(categoryId);
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        Date now = new Date();
        domainService.assertEditableByAuthor(post, userId, now);
        List<PostContentBlockCommand> normalizedBlocks = sanitizeBlocks(blockPolicy.validateAndNormalize(blocks));
        List<UUID> keepAssetIds = mediaAssetIds(normalizedBlocks);
        bindMediaAssets(userId, postId, normalizedBlocks, now);
        postRepository.updatePostMeta(postId, sanitize(title), categoryId, now);
        postContentBlockRepository.replaceBlocks(postId, toDomainBlocks(postId, normalizedBlocks));
        releaseRemovedMediaAssets(userId, postId, keepAssetIds, now);
        postTagRepository.replaceTagsForPost(postId, tags);
        domainEventPublisher.postUpdated(postId);
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
        postBusinessEventLogger.postUpdate(userId, categoryId, postId);
    }

    @Transactional
    public void deleteByAuthor(UUID userId, UUID postId) {
        PostSnapshot post = postRepository.getRequiredSnapshot(postId);
        domainService.assertDeletableByAuthor(post, userId);
        boolean changed = postRepository.markDeletedByAuthor(postId, userId, new Date());
        if (!changed) {
            return;
        }
        domainEventPublisher.postDeleted(postId);
        AfterCommitExecutor.runAfterCommit(() -> socialLikeCleanupActionApi.cleanupEntityLikes(EntityTypes.POST, postId));
        postWriteSideEffectScheduler.schedulePostScoreRefresh(postId);
        postBusinessEventLogger.postDeleteByAuthor(userId, postId);
    }

    private String sanitize(String value) {
        String trimmed = value == null ? "" : value.trim();
        String filtered = sensitiveFilter.filter(textCodec.escapeOnWrite(trimmed));
        return filtered == null ? "" : filtered;
    }

    private List<PostContentBlockCommand> sanitizeBlocks(List<PostContentBlockCommand> blocks) {
        return blocks.stream()
                .map(block -> new PostContentBlockCommand(
                        block.type(),
                        sanitize(block.text()),
                        block.assetId(),
                        block.language(),
                        sanitize(block.caption()),
                        block.displayName(),
                        block.metadata()
                ))
                .toList();
    }

    private String createPostRequestHash(CreatePostCommand command) {
        List<PostContentBlockCommand> blockCommands = command.blocks() == null ? List.of() : command.blocks();
        List<String> tagValues = command.tags() == null ? List.of() : command.tags();
        String blocks = blockCommands.stream()
                .map(this::canonicalBlock)
                .collect(Collectors.joining(","));
        String tags = tagValues.stream()
                .map(this::canonicalValue)
                .collect(Collectors.joining(","));
        String canonical = "content:create_post"
                + "|title=" + canonicalValue(command.title())
                + "|categoryId=" + canonicalValue(command.categoryId())
                + "|tags=[" + tags + "]"
                + "|blocks=[" + blocks + "]";
        return RequestFingerprint.sha256(canonical);
    }

    private String canonicalBlock(PostContentBlockCommand block) {
        return "type=" + canonicalValue(block.type())
                + ";text=" + canonicalValue(block.text())
                + ";assetId=" + canonicalValue(block.assetId())
                + ";language=" + canonicalValue(block.language())
                + ";caption=" + canonicalValue(block.caption())
                + ";displayName=" + canonicalValue(block.displayName())
                + ";metadata=" + canonicalMetadata(block.metadata());
    }

    private String canonicalMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return "<null>";
        }
        return metadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> canonicalValue(entry.getKey()) + "=" + canonicalValue(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String canonicalValue(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    private List<PostContentBlock> toDomainBlocks(UUID postId, List<PostContentBlockCommand> blocks) {
        return IntStream.range(0, blocks.size())
                .mapToObj(index -> {
                    PostContentBlockCommand block = blocks.get(index);
                    return new PostContentBlock(
                        null,
                        postId,
                        index,
                        block.type(),
                        block.text(),
                        block.assetId(),
                        block.language(),
                        block.caption(),
                        block.displayName(),
                        block.metadata()
                    );
                })
                .toList();
    }

    private void bindMediaAssets(UUID userId, UUID postId, List<PostContentBlockCommand> blocks, Date now) {
        List<UUID> assetIds = mediaAssetIds(blocks);
        if (assetIds.isEmpty()) {
            return;
        }
        Map<UUID, PostMediaAsset> assetsById = postMediaAssetRepository.listByIds(assetIds).stream()
                .collect(Collectors.toMap(PostMediaAsset::id, Function.identity(), (left, right) -> left));
        Set<UUID> newlyBoundAssetIds = new HashSet<>();
        for (PostContentBlockCommand block : blocks) {
            if (block.assetId() == null) {
                continue;
            }
            if (!newlyBoundAssetIds.add(block.assetId())) {
                throw new BusinessException(INVALID_ARGUMENT, "媒体资源不能重复使用");
            }
            PostMediaAsset asset = assetsById.get(block.assetId());
            validateMediaAsset(userId, postId, block, asset);
            if (asset.lifecycle() == PostMediaAssetLifecycle.BOUND) {
                continue;
            }
            UUID referenceId = postMediaStoragePort.bindReference(asset, postId, userId);
            try {
                postMediaAssetRepository.bindToPost(
                        asset.id(),
                        postId,
                        referenceId,
                        "video".equals(block.type()) ? PostVideoState.PENDING_TRANSCODE : PostVideoState.NONE,
                        now
                );
            } catch (RuntimeException e) {
                releaseBoundReference(asset, postId, referenceId, userId);
                throw e;
            }
            registerRollbackReferenceRelease(asset, postId, referenceId, userId);
        }
    }

    private void releaseRemovedMediaAssets(UUID userId, UUID postId, List<UUID> keepAssetIds, Date now) {
        Set<UUID> keepAssetIdSet = Set.copyOf(keepAssetIds);
        List<PostMediaAsset> removedAssets = postMediaAssetRepository.listByPostId(postId).stream()
                .filter(asset -> asset.lifecycle() == PostMediaAssetLifecycle.BOUND)
                .filter(asset -> !keepAssetIdSet.contains(asset.id()))
                .toList();
        postMediaAssetRepository.releaseRemovedFromPost(postId, keepAssetIds, now);
        AfterCommitExecutor.runAfterCommit(() -> removedAssets.forEach(asset -> {
            try {
                postMediaStoragePort.releaseReference(asset, userId);
            } catch (RuntimeException ignored) {
            }
        }));
    }

    private void validateMediaAsset(UUID userId, UUID postId, PostContentBlockCommand block, PostMediaAsset asset) {
        if (asset == null) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体资源不存在");
        }
        if (!userId.equals(asset.ownerUserId())) {
            throw new BusinessException(FORBIDDEN, "只能使用自己的媒体资源");
        }
        if (asset.lifecycle() != PostMediaAssetLifecycle.UPLOADED && asset.lifecycle() != PostMediaAssetLifecycle.BOUND) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体资源尚未上传完成");
        }
        if (asset.postId() != null && !asset.postId().equals(postId)) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体资源已被其他帖子使用");
        }
        PostMediaKind expectedKind = switch (block.type().toLowerCase(Locale.ROOT)) {
            case "image" -> PostMediaKind.IMAGE;
            case "video" -> PostMediaKind.VIDEO;
            case "file" -> PostMediaKind.FILE;
            default -> null;
        };
        if (expectedKind != null && asset.mediaKind() != expectedKind) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体类型与内容块不匹配");
        }
    }

    private List<UUID> mediaAssetIds(List<PostContentBlockCommand> blocks) {
        return blocks.stream()
                .map(PostContentBlockCommand::assetId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private void releaseBoundReference(PostMediaAsset asset, UUID postId, UUID referenceId, UUID actorUserId) {
        try {
            postMediaStoragePort.releaseReference(new PostMediaAsset(
                    asset.id(),
                    asset.ownerUserId(),
                    postId,
                    asset.ossObjectId(),
                    asset.ossVersionId(),
                    referenceId,
                    asset.uploadSessionId(),
                    asset.fileName(),
                    asset.contentType(),
                    asset.contentLength(),
                    asset.mediaKind(),
                    asset.lifecycle(),
                    asset.videoState(),
                    asset.publicUrl(),
                    asset.failureReason(),
                    asset.createTime(),
                    asset.updateTime()
            ), actorUserId);
        } catch (RuntimeException ignored) {
        }
    }

    private void registerRollbackReferenceRelease(PostMediaAsset asset, UUID postId, UUID referenceId, UUID actorUserId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    releaseBoundReference(asset, postId, referenceId, actorUserId);
                }
            }
        });
    }
}
