package com.nowcoder.community.content.application.port;

import com.nowcoder.community.content.domain.model.ModerationActionRecord;
import com.nowcoder.community.content.domain.model.ModerationTarget;
import com.nowcoder.community.content.domain.model.ReportSnapshot;

import java.util.UUID;

public interface ModerationNoticePort {

    void publish(ReportSnapshot report, ModerationActionRecord action, ModerationTarget target, String kind, UUID toUserId);
}
