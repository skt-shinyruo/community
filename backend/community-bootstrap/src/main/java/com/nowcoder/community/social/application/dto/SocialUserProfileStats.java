package com.nowcoder.community.social.application.dto;

public class SocialUserProfileStats {

    private long likeCount;
    private long followeeCount;
    private long followerCount;
    private boolean hasFollowed;

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

    public boolean isHasFollowed() {
        return hasFollowed;
    }

    public void setHasFollowed(boolean hasFollowed) {
        this.hasFollowed = hasFollowed;
    }
}
