package com.nowcoder.community.notice.domain.repository;

import com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState;

public interface LikeNoticeProjectionStateRepository {

    LikeNoticeProjectionState.Transition advance(LikeNoticeProjectionState incoming);
}
