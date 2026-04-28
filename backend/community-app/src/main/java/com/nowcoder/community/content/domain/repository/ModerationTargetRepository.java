package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.ModerationTarget;
import com.nowcoder.community.content.domain.model.ReportSnapshot;

public interface ModerationTargetRepository {

    ModerationTarget resolveTarget(ReportSnapshot report);
}
