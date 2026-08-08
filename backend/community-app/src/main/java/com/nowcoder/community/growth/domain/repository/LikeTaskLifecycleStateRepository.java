package com.nowcoder.community.growth.domain.repository;

import com.nowcoder.community.growth.domain.model.LikeTaskLifecycleState;

public interface LikeTaskLifecycleStateRepository {

    record AdvanceResult(
            LikeTaskLifecycleState.Transition transition,
            LikeTaskLifecycleState previous,
            LikeTaskLifecycleState current
    ) {
    }

    AdvanceResult advance(LikeTaskLifecycleState incoming);
}
