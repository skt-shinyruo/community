package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.application.command.PreparePostMediaUploadCommand;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostMediaUploadStatus;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;

@Service
public class PostMediaApplicationService {

    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    static final String MIME_TYPES = "image/png;image/jpeg;image/webp;image/gif;video/mp4;video/webm;application/pdf;application/zip";

    private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> VIDEO_MIME_TYPES = Set.of("video/mp4", "video/webm");
    private static final Set<String> FILE_MIME_TYPES = Set.of("application/pdf", "application/zip");
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final PostMediaStoragePort storagePort;
    private final UuidV7Generator idGenerator;
    private final PostMediaUploadTransactionOperations transactionOperations;

    @Autowired
    public PostMediaApplicationService(
            PostMediaAssetRepository assetRepository,
            PostMediaStoragePort storagePort,
            PostMediaUploadTransactionOperations transactionOperations
    ) {
        this(assetRepository, storagePort, new UuidV7Generator(), transactionOperations);
    }

    public PostMediaApplicationService(PostMediaAssetRepository assetRepository, PostMediaStoragePort storagePort) {
        this(assetRepository, storagePort, new UuidV7Generator(), new PostMediaUploadTransactionOperations(assetRepository));
    }

    PostMediaApplicationService(PostMediaAssetRepository assetRepository,
                                PostMediaStoragePort storagePort,
                                UuidV7Generator idGenerator) {
        this(assetRepository, storagePort, idGenerator, new PostMediaUploadTransactionOperations(assetRepository));
    }

    PostMediaApplicationService(
            PostMediaAssetRepository assetRepository,
            PostMediaStoragePort storagePort,
            UuidV7Generator idGenerator,
            PostMediaUploadTransactionOperations transactionOperations
    ) {
        this.storagePort = storagePort;
        this.idGenerator = idGenerator;
        this.transactionOperations = transactionOperations;
    }

    public PostMediaUploadSessionResult prepareUpload(PreparePostMediaUploadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.actorUserId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        String fileName = normalizeFileName(command.fileName());
        String contentType = normalizeContentType(command.contentType());
        PostMediaKind mediaKind = inferKind(command.mediaKind(), contentType);
        validateContentLength(command.contentLength(), mediaKind);

        UUID assetId = command.requestId() == null ? idGenerator.next() : command.requestId();
        Date now = new Date();
        PostMediaAsset draft = new PostMediaAsset(
                assetId,
                command.actorUserId(),
                null,
                null,
                null,
                null,
                null,
                fileName,
                contentType,
                command.contentLength(),
                mediaKind,
                PostMediaAssetLifecycle.DRAFT,
                PostVideoState.NONE,
                "",
                "",
                now,
                null
        );
        PostMediaUploadSessionResult session = storagePort.prepareUpload(draft, normalizeChecksum(command.checksumSha256()));
        PostMediaAsset preparedDraft = draftWithPreparedUploadSession(draft, session);
        try {
            transactionOperations.createDraft(preparedDraft);
            return session;
        } catch (RuntimeException e) {
            PostMediaAsset replay = loadReplay(assetId);
            if (samePrepareRequest(replay, preparedDraft)) {
                return session;
            }
            throw e;
        }
    }

    public void completeUpload(UUID actorUserId, UUID assetId, UUID uploadSessionId, PostMediaUploadContent content) {
        if (actorUserId == null || assetId == null || uploadSessionId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/assetId/uploadSessionId 非法");
        }
        validateUploadContent(content);
        PostMediaAsset asset = transactionOperations.load(assetId);
        if (!Objects.equals(actorUserId, asset.ownerUserId())) {
            throw new BusinessException(FORBIDDEN, "只能上传自己的媒体资源");
        }
        if (!Objects.equals(uploadSessionId, asset.uploadSessionId())) {
            throw new BusinessException(INVALID_ARGUMENT, "上传会话与媒体资源不匹配");
        }
        if (asset.uploadStatus() == PostMediaUploadStatus.COMPLETED) {
            return;
        }
        if (asset.uploadStatus() == PostMediaUploadStatus.OBJECT_COMPLETED) {
            if (!transactionOperations.markCompleted(
                    asset.id(), asset.uploadOperationVersion(), new Date())) {
                throw new BusinessException(INTERNAL_ERROR, "媒体上传完成状态冲突");
            }
            return;
        }
        if (asset.lifecycle() != PostMediaAssetLifecycle.DRAFT
                || asset.uploadStatus() != PostMediaUploadStatus.PREPARED) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体资源状态不允许上传");
        }
        if (!Objects.equals(asset.contentType(), normalizeContentType(content.contentType()))
                || asset.contentLength() != content.size()) {
            throw new BusinessException(INVALID_ARGUMENT, "上传内容与媒体资源不匹配");
        }

        Date claimedAt = new Date();
        if (!transactionOperations.claimCompletion(
                assetId, actorUserId, asset.uploadOperationVersion(), claimedAt)) {
            PostMediaAsset current = transactionOperations.load(assetId);
            if (current.uploadStatus() == PostMediaUploadStatus.COMPLETED) {
                return;
            }
            throw new BusinessException(INVALID_ARGUMENT, "媒体资源正在上传或状态已变化");
        }
        long claimedVersion = asset.uploadOperationVersion() + 1L;

        PostMediaStoragePort.UploadedPostMedia uploaded = storagePort.completeUpload(asset, uploadSessionId, content);
        if (uploaded == null || uploaded.versionId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "上传结果非法");
        }
        Date objectCompletedAt = new Date();
        if (!transactionOperations.markObjectCompleted(
                assetId,
                claimedVersion,
                uploaded.versionId(),
                uploaded.publicUrl(),
                uploaded.contentType(),
                uploaded.contentLength(),
                objectCompletedAt)) {
            throw new BusinessException(INTERNAL_ERROR, "媒体上传对象状态冲突");
        }
        if (!transactionOperations.markCompleted(assetId, claimedVersion, new Date())) {
            throw new BusinessException(INTERNAL_ERROR, "媒体上传完成状态冲突");
        }
    }

    private PostMediaAsset loadReplay(UUID assetId) {
        try {
            return transactionOperations.load(assetId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean samePrepareRequest(PostMediaAsset replay, PostMediaAsset expected) {
        return replay != null
                && Objects.equals(replay.id(), expected.id())
                && Objects.equals(replay.ownerUserId(), expected.ownerUserId())
                && Objects.equals(replay.ossObjectId(), expected.ossObjectId())
                && Objects.equals(replay.ossVersionId(), expected.ossVersionId())
                && Objects.equals(replay.uploadSessionId(), expected.uploadSessionId())
                && Objects.equals(replay.fileName(), expected.fileName())
                && Objects.equals(replay.contentType(), expected.contentType())
                && replay.contentLength() == expected.contentLength()
                && replay.mediaKind() == expected.mediaKind();
    }

    private static PostMediaAsset draftWithPreparedUploadSession(PostMediaAsset draft, PostMediaUploadSessionResult session) {
        if (session == null
                || !Objects.equals(draft.id(), session.assetId())
                || session.ossObjectId() == null
                || session.ossVersionId() == null) {
            throw new BusinessException(INTERNAL_ERROR, "签发媒体上传参数失败");
        }
        return new PostMediaAsset(
                draft.id(),
                draft.ownerUserId(),
                draft.postId(),
                session.ossObjectId(),
                session.ossVersionId(),
                draft.ossReferenceId(),
                parseUploadSessionId(session.uploadId()),
                draft.fileName(),
                draft.contentType(),
                draft.contentLength(),
                draft.mediaKind(),
                draft.lifecycle(),
                draft.videoState(),
                draft.publicUrl(),
                draft.failureReason(),
                draft.createTime(),
                draft.updateTime()
        );
    }

    private static UUID parseUploadSessionId(String uploadId) {
        try {
            return UUID.fromString(uploadId);
        } catch (RuntimeException e) {
            throw new BusinessException(INTERNAL_ERROR, "签发媒体上传参数失败", e);
        }
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null) {
            throw new BusinessException(INVALID_ARGUMENT, "文件名不能为空");
        }
        String normalized = fileName.trim();
        if (normalized.isEmpty()
                || normalized.length() > MAX_FILE_NAME_LENGTH
                || normalized.contains("..")
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.contains("\u0000")) {
            throw new BusinessException(INVALID_ARGUMENT, "文件名非法");
        }
        return normalized;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            throw new BusinessException(INVALID_ARGUMENT, "文件类型不能为空");
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (!IMAGE_MIME_TYPES.contains(normalized) && !VIDEO_MIME_TYPES.contains(normalized) && !FILE_MIME_TYPES.contains(normalized)) {
            throw new BusinessException(INVALID_ARGUMENT, "不支持的文件类型");
        }
        return normalized;
    }

    private static PostMediaKind inferKind(String requestedKind, String contentType) {
        PostMediaKind inferred = inferKindFromContentType(contentType);
        if (requestedKind == null || requestedKind.isBlank()) {
            return inferred;
        }
        PostMediaKind explicit;
        try {
            explicit = PostMediaKind.valueOf(requestedKind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体类型非法");
        }
        if (explicit != inferred) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体类型与文件类型不匹配");
        }
        return explicit;
    }

    private static PostMediaKind inferKindFromContentType(String contentType) {
        if (IMAGE_MIME_TYPES.contains(contentType)) {
            return PostMediaKind.IMAGE;
        }
        if (VIDEO_MIME_TYPES.contains(contentType)) {
            return PostMediaKind.VIDEO;
        }
        return PostMediaKind.FILE;
    }

    private static void validateContentLength(long contentLength, PostMediaKind mediaKind) {
        if (contentLength <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "文件不能为空");
        }
        long maxBytes = switch (mediaKind) {
            case IMAGE -> MAX_IMAGE_BYTES;
            case VIDEO -> MAX_VIDEO_BYTES;
            case FILE -> MAX_FILE_BYTES;
        };
        if (contentLength > maxBytes) {
            throw new BusinessException(INVALID_ARGUMENT, "文件过大（maxBytes=" + maxBytes + "）");
        }
    }

    private static void validateUploadContent(PostMediaUploadContent content) {
        if (content == null || content.empty()) {
            throw new BusinessException(INVALID_ARGUMENT, "文件不能为空");
        }
        String contentType = normalizeContentType(content.contentType());
        validateContentLength(content.size(), inferKindFromContentType(contentType));
    }

    private static String normalizeChecksum(String checksumSha256) {
        return checksumSha256 == null ? "" : checksumSha256.trim();
    }

}
