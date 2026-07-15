package com.nowcoder.community.profile.controller.dto;

import java.util.Date;
import java.util.UUID;

public class UserProfileResponse {

    private UUID id;
    private String username;
    private String headerUrl;
    private int type;
    private int status;
    private Date createTime;
    private boolean userLevelEnabled;
    private Integer userLevel;
    private Integer signInDaysInWindow;

    private long likeCount;
    private long followeeCount;
    private long followerCount;
    private Boolean hasFollowed;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
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

    public boolean isUserLevelEnabled() {
        return userLevelEnabled;
    }

    public void setUserLevelEnabled(boolean userLevelEnabled) {
        this.userLevelEnabled = userLevelEnabled;
    }

    public Integer getUserLevel() {
        return userLevel;
    }

    public void setUserLevel(Integer userLevel) {
        this.userLevel = userLevel;
    }

    public Integer getSignInDaysInWindow() {
        return signInDaysInWindow;
    }

    public void setSignInDaysInWindow(Integer signInDaysInWindow) {
        this.signInDaysInWindow = signInDaysInWindow;
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

}
