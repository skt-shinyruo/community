package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.CommentView;

import java.util.List;
import java.util.UUID;

public interface CommentReadQueryApi {

    List<CommentView> comments(UUID postId, Integer page, Integer size);

    List<CommentView> replies(UUID postId, UUID commentId, Integer page, Integer size);
}
