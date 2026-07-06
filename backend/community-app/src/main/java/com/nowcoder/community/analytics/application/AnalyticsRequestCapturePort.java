package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.command.RecordRequestCommand;

public interface AnalyticsRequestCapturePort {

    void publish(RecordRequestCommand command);
}
