package com.nowcoder.community.user.dto;

import java.util.Date;

public class UserProfileResponse {

    private int id;
    private String username;
    private String headerUrl;
    private int type;
    private int status;
    private Date createTime;
    private int score;
    private int level;

    // 对齐旧单体“用户主页”展示字段：获赞/关注/粉丝/关注状态
    private long likeCount;
    private long followeeCount;
    private long followerCount;
    private Boolean hasFollowed;

    /**
     * 兼容旧响应结构保留的字段；本地聚合成功时固定为 false。
     */
    private boolean socialDegraded;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHeaderUrl() {
        return headerUrl;
    }

    public void setHeaderUrl(String headerUrl) {
        this.headerUrl = headerUrl;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getFolloweeCount() {
        return followeeCount;
    }

    public void setFolloweeCount(long followeeCount) {
        this.followeeCount = followeeCount;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(long followerCount) {
        this.followerCount = followerCount;
    }

    public Boolean getHasFollowed() {
        return hasFollowed;
    }

    public void setHasFollowed(Boolean hasFollowed) {
        this.hasFollowed = hasFollowed;
    }

    public boolean isSocialDegraded() {
        return socialDegraded;
    }

    public void setSocialDegraded(boolean socialDegraded) {
        this.socialDegraded = socialDegraded;
    }
}
