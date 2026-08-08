package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.model.LikeTaskLifecycleState;
import com.nowcoder.community.growth.domain.repository.LikeTaskLifecycleStateRepository;
import com.nowcoder.community.growth.infrastructure.persistence.dataobject.LikeTaskLifecycleStateDataObject;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.LikeTaskLifecycleStateMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisLikeTaskLifecycleStateRepository implements LikeTaskLifecycleStateRepository {

    private final LikeTaskLifecycleStateMapper mapper;

    public MyBatisLikeTaskLifecycleStateRepository(LikeTaskLifecycleStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AdvanceResult advance(LikeTaskLifecycleState incoming) {
        mapper.ensureSlot(incoming.recipientUserId(), incoming.relationKey());
        LikeTaskLifecycleStateDataObject currentData = mapper.selectForUpdate(
                incoming.recipientUserId(), incoming.relationKey());
        LikeTaskLifecycleState previous = currentData == null ? null : currentData.toDomain();
        LikeTaskLifecycleState.Transition transition = LikeTaskLifecycleState.decide(previous, incoming);
        LikeTaskLifecycleState current = transition == LikeTaskLifecycleState.Transition.IGNORED
                ? previous
                : incoming;
        if (transition != LikeTaskLifecycleState.Transition.IGNORED) {
            int updated = mapper.update(LikeTaskLifecycleStateDataObject.from(incoming));
            if (updated != 1) {
                throw new IllegalStateException("like task lifecycle state update lost its locked row");
            }
        }
        return new AdvanceResult(transition, previous, current);
    }
}
