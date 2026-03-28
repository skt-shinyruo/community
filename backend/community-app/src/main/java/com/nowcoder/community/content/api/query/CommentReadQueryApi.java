package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.CommentView;

import java.util.List;

public interface CommentReadQueryApi {

    List<CommentView> comments(int postId, Integer page, Integer size);

    List<CommentView> replies(int postId, int commentId, Integer page, Integer size);
}
