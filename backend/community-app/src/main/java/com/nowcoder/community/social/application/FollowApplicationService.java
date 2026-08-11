package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.pagination.Pagination;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.FollowRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.FollowDomainService;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service("socialFollowApplicationService")
public class FollowApplicationService implements SocialFollowQueryApi {

    private static final int MAX_BATCH_ENTITY_IDS = 200;
    private static final int MAX_LEGACY_PAGE = 100;
    private static final int MAX_PAGE_SIZE = 50;
    private static final FollowCursorCodec CURSOR_CODEC = new FollowCursorCodec();

    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final FollowDomainService followDomainService;
    private final BlockDomainService blockDomainService;
    private final SocialDomainEventPublisher eventPublisher;
    private final UserLookupQueryApi userLookupQueryApi;
    private final Clock clock;

    public FollowApplicationService(
            FollowRepository followRepository,
            BlockRepository blockRepository,
            FollowDomainService followDomainService,
            BlockDomainService blockDomainService,
            SocialDomainEventPublisher eventPublisher,
            UserLookupQueryApi userLookupQueryApi,
            Clock clock
    ) {
        this.followRepository = Objects.requireNonNull(followRepository, "followRepository must not be null");
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository must not be null");
        this.followDomainService = Objects.requireNonNull(followDomainService, "followDomainService must not be null");
        this.blockDomainService = Objects.requireNonNull(blockDomainService, "blockDomainService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.userLookupQueryApi = Objects.requireNonNull(userLookupQueryApi, "userLookupQueryApi must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public void follow(FollowCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID actorUserId = command.actorUserId();
        int entityType = command.entityType();
        UUID entityId = command.entityId();
        followDomainService.validateFollow(actorUserId, entityType, entityId);

        blockRepository.lockUserPair(actorUserId, entityId);
        boolean existed = followRepository.hasFollowed(actorUserId, entityType, entityId);
        if (!existed) {
            requireFollowTargetUserExists(entityId);
            if (blockDomainService.isEitherBlocked(actorUserId, entityId, blockRepository)) {
                throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
            }
        }

        long now = clock.millis();
        boolean created = followRepository.follow(actorUserId, entityType, entityId, now);
        if (!created) {
            return;
        }

        FollowCreatedDomainEvent event = followDomainService.followCreatedEvent(
                actorUserId,
                entityType,
                entityId,
                Instant.ofEpochMilli(now)
        );
        eventPublisher.publishFollowCreated(event);
    }

    @Transactional
    public void unfollow(UnfollowCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        followDomainService.validateUnfollow(command.actorUserId(), command.entityType(), command.entityId());
        followRepository.unfollow(command.actorUserId(), command.entityType(), command.entityId());
    }

    @Override
    public boolean hasFollowed(UUID actorUserId, int entityType, UUID entityId) {
        validateFollowRelationQuery(actorUserId, entityType, entityId);
        return followRepository.hasFollowed(actorUserId, entityType, entityId);
    }

    public Map<UUID, Boolean> statuses(UUID actorUserId, int entityType, List<UUID> entityIds) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        validateUserOnlyEntityType(entityType);
        List<UUID> normalizedIds = normalizeBatchEntityIds(entityIds);
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        return followRepository.followedStatusesBatch(actorUserId, entityType, normalizedIds);
    }

    @Override
    public long followeeCount(UUID userId, int entityType) {
        validateFollowUserQuery(userId, entityType);
        return followRepository.countFolloweesExcludingBlocked(userId, entityType, blockRepository);
    }

    @Override
    public long followerCount(int entityType, UUID entityId) {
        validateFollowTargetQuery(entityType, entityId);
        return followRepository.countFollowersExcludingBlocked(entityType, entityId, blockRepository);
    }

    @Override
    public List<UUID> listFolloweeIds(UUID userId, int limit) {
        validateFollowUserQuery(userId, USER);
        int safeLimit = Math.min(200, Math.max(1, limit));
        return followRepository.listFolloweeIdsExcludingBlocked(userId, USER, blockRepository, safeLimit);
    }

    public List<FollowRelationResult> listFollowees(UUID userId, int entityType, int page, int size) {
        validateFollowUserQuery(userId, entityType);
        int p = legacyPage(page);
        int s = normalizePageSize(size);
        return followRepository.listFolloweesExcludingBlocked(userId, entityType, blockRepository, Pagination.safeOffset(p, s), s)
                .stream()
                .map(this::toResult)
                .toList();
    }

    public List<FollowRelationResult> listFollowers(int entityType, UUID entityId, int page, int size) {
        validateFollowTargetQuery(entityType, entityId);
        int p = legacyPage(page);
        int s = normalizePageSize(size);
        return followRepository.listFollowersExcludingBlocked(entityType, entityId, blockRepository, Pagination.safeOffset(p, s), s)
                .stream()
                .map(this::toResult)
                .toList();
    }

    public FollowRelationPageResult listFolloweePage(
            UUID userId,
            int entityType,
            String cursor,
            int size
    ) {
        validateFollowUserQuery(userId, entityType);
        int limit = normalizePageSize(size);
        FollowCursorCodec.Boundary boundary = decodeBoundary(cursor);
        List<FollowRelation> relations = followRepository.listFolloweesAfterExcludingBlocked(
                userId,
                entityType,
                blockRepository,
                boundary == null ? null : boundary.followTime(),
                boundary == null ? null : boundary.targetId(),
                limit + 1
        );
        return toPage(relations, limit);
    }

    public FollowRelationPageResult listFollowerPage(
            int entityType,
            UUID entityId,
            String cursor,
            int size
    ) {
        validateFollowTargetQuery(entityType, entityId);
        int limit = normalizePageSize(size);
        FollowCursorCodec.Boundary boundary = decodeBoundary(cursor);
        List<FollowRelation> relations = followRepository.listFollowersAfterExcludingBlocked(
                entityType,
                entityId,
                blockRepository,
                boundary == null ? null : boundary.followTime(),
                boundary == null ? null : boundary.targetId(),
                limit + 1
        );
        return toPage(relations, limit);
    }

    private FollowRelationPageResult toPage(List<FollowRelation> relations, int limit) {
        List<FollowRelation> safeRelations = relations == null ? List.of() : relations;
        boolean hasNext = safeRelations.size() > limit;
        List<FollowRelation> page = hasNext
                ? List.copyOf(safeRelations.subList(0, limit))
                : List.copyOf(safeRelations);
        String nextCursor = hasNext && !page.isEmpty()
                ? CURSOR_CODEC.encode(page.get(page.size() - 1).followTime(), page.get(page.size() - 1).targetId())
                : "";
        return new FollowRelationPageResult(page.stream().map(this::toResult).toList(), nextCursor, hasNext);
    }

    private FollowCursorCodec.Boundary decodeBoundary(String cursor) {
        try {
            return CURSOR_CODEC.decode(cursor).orElse(null);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(INVALID_ARGUMENT, "follow cursor is invalid");
        }
    }

    private int legacyPage(int page) {
        int normalized = Math.max(0, page);
        if (normalized > MAX_LEGACY_PAGE) {
            throw new BusinessException(INVALID_ARGUMENT, "page exceeds legacy limit; use cursor pagination");
        }
        return normalized;
    }

    private int normalizePageSize(int size) {
        return Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    private void validateFollowRelationQuery(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null || entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        validateUserOnlyEntityType(entityType);
    }

    private void validateFollowUserQuery(UUID userId, int entityType) {
        if (userId == null || entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        validateUserOnlyEntityType(entityType);
    }

    private void validateFollowTargetQuery(int entityType, UUID entityId) {
        if (entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        validateUserOnlyEntityType(entityType);
    }

    private void validateUserOnlyEntityType(int entityType) {
        if (entityType != USER) {
            throw new BusinessException(INVALID_ARGUMENT, "follow 仅支持 USER");
        }
    }

    private List<UUID> normalizeBatchEntityIds(List<UUID> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        if (entityIds.size() > MAX_BATCH_ENTITY_IDS) {
            throw new BusinessException(INVALID_ARGUMENT, "entityIds 不能超过200");
        }
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>();
        for (UUID entityId : entityIds) {
            if (entityId == null) {
                throw new BusinessException(INVALID_ARGUMENT, "entityIds 非法");
            }
            uniqueIds.add(entityId);
        }
        return new ArrayList<>(uniqueIds);
    }

    private void requireFollowTargetUserExists(UUID entityId) {
        if (userLookupQueryApi.getSummaryById(entityId) == null) {
            throw new BusinessException(NOT_FOUND, "follow target user not found: userId=" + entityId);
        }
    }

    private FollowRelationResult toResult(FollowRelation relation) {
        return new FollowRelationResult(relation.targetId(), relation.followTime());
    }

    public record FollowCommand(UUID actorUserId, int entityType, UUID entityId) {
    }

    public record UnfollowCommand(UUID actorUserId, int entityType, UUID entityId) {
    }

    public record FollowRelationResult(UUID targetId, Instant followTime) {
    }

    public record FollowRelationPageResult(
            List<FollowRelationResult> items,
            String nextCursor,
            boolean hasNext
    ) {
        public FollowRelationPageResult {
            items = items == null ? List.of() : List.copyOf(items);
            nextCursor = nextCursor == null ? "" : nextCursor;
        }
    }

}
