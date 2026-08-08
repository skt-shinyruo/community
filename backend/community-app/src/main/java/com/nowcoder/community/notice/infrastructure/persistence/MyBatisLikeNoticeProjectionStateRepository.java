package com.nowcoder.community.notice.infrastructure.persistence;

import com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState;
import com.nowcoder.community.notice.domain.repository.LikeNoticeProjectionStateRepository;
import com.nowcoder.community.notice.infrastructure.persistence.dataobject.LikeNoticeProjectionStateDataObject;
import com.nowcoder.community.notice.infrastructure.persistence.mapper.LikeNoticeProjectionStateMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisLikeNoticeProjectionStateRepository implements LikeNoticeProjectionStateRepository {

    private final LikeNoticeProjectionStateMapper mapper;

    public MyBatisLikeNoticeProjectionStateRepository(LikeNoticeProjectionStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public LikeNoticeProjectionState.Transition advance(LikeNoticeProjectionState incoming) {
        mapper.ensureSlot(incoming.recipientUserId(), incoming.sourceRelationKey());
        LikeNoticeProjectionStateDataObject currentData = mapper.selectForUpdate(
                incoming.recipientUserId(), incoming.sourceRelationKey());
        LikeNoticeProjectionState current = currentData == null ? null : currentData.toDomain();
        LikeNoticeProjectionState.Transition transition = LikeNoticeProjectionState.decide(current, incoming);
        if (transition != LikeNoticeProjectionState.Transition.IGNORED) {
            int updated = mapper.update(LikeNoticeProjectionStateDataObject.from(incoming));
            if (updated != 1) {
                throw new IllegalStateException("like notice projection state update lost its locked row");
            }
        }
        return transition;
    }
}
