package com.nowcoder.community.social.infrastructure.persistence.dataobject;

import java.util.Date;
import java.util.UUID;

/**
 * DB 查询结果承载：用于将 social_follow 的行映射为可转换的对象。
 */
public class FollowRelationDataObject {

    private UUID targetId;
    private Date followTime;

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public Date getFollowTime() {
        return followTime;
    }

    public void setFollowTime(Date followTime) {
        this.followTime = followTime;
    }
}
