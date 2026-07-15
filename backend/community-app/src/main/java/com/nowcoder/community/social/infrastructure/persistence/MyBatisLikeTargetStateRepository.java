package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.domain.model.LikeTargetState;
import com.nowcoder.community.social.domain.repository.LikeTargetStateRepository;
import com.nowcoder.community.social.infrastructure.persistence.dataobject.LikeTargetStateDataObject;
import com.nowcoder.community.social.infrastructure.persistence.mapper.LikeTargetStateMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisLikeTargetStateRepository implements LikeTargetStateRepository {

    private final LikeTargetStateMapper mapper;

    public MyBatisLikeTargetStateRepository(LikeTargetStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean insertActiveIfAbsent(int entityType, UUID entityId) {
        try {
            return mapper.insertActive(entityType, entityId) > 0;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    @Override
    public Optional<LikeTargetState> findByTarget(int entityType, UUID entityId) {
        return Optional.ofNullable(toDomain(mapper.selectByTarget(entityType, entityId)));
    }

    @Override
    public LikeTargetState findForUpdate(int entityType, UUID entityId) {
        LikeTargetState state = toDomain(mapper.selectForUpdate(entityType, entityId));
        if (state == null) {
            throw new IllegalStateException("like target state missing after initialization");
        }
        return state;
    }

    @Override
    public boolean saveIfNewer(LikeTargetState state) {
        if (state == null || !state.isDeleted()) {
            return false;
        }
        return mapper.updateDeletedIfNewer(
                state.entityType(),
                state.entityId(),
                state.sourceEventId(),
                state.sourceVersion(),
                state.deletedAt()
        ) > 0;
    }

    @Override
    public List<LikeTargetState> scanDeletedTargetsWithLikesAfter(int entityType, UUID afterEntityId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        UUID cursor = afterEntityId == null ? new UUID(0L, 0L) : afterEntityId;
        List<LikeTargetStateDataObject> rows = mapper.scanDeletedTargetsWithLikesAfter(entityType, cursor, limit);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::toDomain).toList();
    }

    private LikeTargetState toDomain(LikeTargetStateDataObject row) {
        if (row == null) {
            return null;
        }
        return LikeTargetState.restore(
                row.getEntityType(),
                row.getEntityId(),
                row.getStatus(),
                row.getSourceEventId(),
                row.getSourceVersion(),
                row.getDeletedAt()
        );
    }
}
