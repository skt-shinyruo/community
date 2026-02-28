package com.nowcoder.community.content.score;

public interface PostScoreQueue {

    void add(int postId);

    Integer pop();
}

