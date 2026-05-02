package com.nowcoder.community.search.application;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
import org.springframework.stereotype.Service;

@Service
public class SearchPostProjectionApplicationService {

    private final PostScanQueryApi postScanQueryApi;
    private final SearchApplicationService searchApplicationService;

    public SearchPostProjectionApplicationService(
            PostScanQueryApi postScanQueryApi,
            SearchApplicationService searchApplicationService
    ) {
        this.postScanQueryApi = postScanQueryApi;
        this.searchApplicationService = searchApplicationService;
    }

    public void projectPostFromOutbox(ProjectPostOutboxCommand command) {
        if (command == null || command.postId() == null) {
            return;
        }
        PostScanView.PostProjectionView projection = postScanQueryApi.getPostProjectionAllowDeleted(command.postId());
        if (projection == null || projection.postId() == null) {
            searchApplicationService.deletePost(new DeleteIndexedPostCommand(command.postId()));
            return;
        }
        searchApplicationService.syncPostProjection(PostSearchPayloadMapper.toSyncCommand(projection));
    }
}
