package com.nowcoder.yierloom.api;

public interface EventSink {
    boolean emit(DiagnosticEvent event);
}
