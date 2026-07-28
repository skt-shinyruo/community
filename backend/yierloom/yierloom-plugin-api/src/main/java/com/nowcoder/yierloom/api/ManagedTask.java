package com.nowcoder.yierloom.api;

public interface ManagedTask {
    String name();

    boolean cancel();

    boolean isCancelled();
}
