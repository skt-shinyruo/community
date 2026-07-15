package com.nowcoder.community.content.application;

public interface HotFeedProjectionCompletion {

    void afterTransaction(Runnable committedAction, Runnable rolledBackAction);
}
