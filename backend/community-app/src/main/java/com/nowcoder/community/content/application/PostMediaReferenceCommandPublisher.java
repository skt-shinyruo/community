package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;

public interface PostMediaReferenceCommandPublisher {

    void publish(PostMediaReferenceCommand command);
}
