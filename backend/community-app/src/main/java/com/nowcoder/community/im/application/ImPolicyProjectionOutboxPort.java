package com.nowcoder.community.im.application;

public interface ImPolicyProjectionOutboxPort {

    void enqueue(ImPolicyProjectionEvent event);
}
