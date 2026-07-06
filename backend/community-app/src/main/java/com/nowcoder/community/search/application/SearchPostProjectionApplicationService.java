package com.nowcoder.community.search.application;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class SearchPostProjectionApplicationService {

    private final PostScanQueryApi postScanQueryApi;
    private final SearchApplicationService searchApplicationService;
    private final SearchPolicyProperties policyProperties;

    SearchPostProjectionApplicationService(
            PostScanQueryApi postScanQueryApi,
            SearchApplicationService searchApplicationService
    ) {
        this(postScanQueryApi, searchApplicationService, new SearchPolicyProperties());
    }

    @Autowired
    public SearchPostProjectionApplicationService(
            PostScanQueryApi postScanQueryApi,
            SearchApplicationService searchApplicationService,
            SearchPolicyProperties policyProperties
    ) {
        this.postScanQueryApi = postScanQueryApi;
        this.searchApplicationService = searchApplicationService;
        this.policyProperties = policyProperties == null ? new SearchPolicyProperties() : policyProperties;
    }

    public void projectPostFromOutbox(ProjectPostOutboxCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!StringUtils.hasText(command.sourceEventId())) {
            throw new IllegalStateException("search projection source event id is blank");
        }
        if (!policyProperties.isProjectionEnabled()) {
            return;
        }
        if (command.postId() == null) {
            return;
        }
        PostScanView.PostProjectionView projection = postScanQueryApi.getPostProjectionAllowDeleted(command.postId());
        if (projection == null || projection.postId() == null) {
            searchApplicationService.deletePost(new DeleteIndexedPostCommand(command.postId()));
            return;
        }
        searchApplicationService.syncPostProjection(PostSearchPayloadAssembler.toSyncCommand(projection));
    }
}
