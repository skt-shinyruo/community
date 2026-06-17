package com.nowcoder.community.notice.application;

public interface NoticeProjectionEventRecorder {

    boolean tryRecord(String sourceEventId);
}
