package com.nowcoder.community.search.application;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

@Service
public class SearchPostProjectionApplicationService {

    private final PostScanQueryApi postScanQueryApi;
    private final SearchApplicationService searchApplicationService;
    private final SearchPolicyProperties policyProperties;

    public SearchPostProjectionApplicationService(
            PostScanQueryApi postScanQueryApi,
            SearchApplicationService searchApplicationService,
            SearchPolicyProperties policyProperties
    ) {
        this.postScanQueryApi = Objects.requireNonNull(postScanQueryApi, "postScanQueryApi must not be null");
        this.searchApplicationService = Objects.requireNonNull(
                searchApplicationService,
                "searchApplicationService must not be null"
        );
        this.policyProperties = Objects.requireNonNull(policyProperties, "policyProperties must not be null");
    }

    public void projectPost(ProjectPostCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!StringUtils.hasText(command.sourceEventId()) || command.sourceVersion() <= 0L) {
            throw new IllegalArgumentException("search projection source metadata is invalid");
        }
        if (!policyProperties.isProjectionEnabled()) {
            return;
        }
        if (command.postId() == null) {
            return;
        }
        PostScanView.PostProjectionView projection = postScanQueryApi.getPostProjectionAllowDeleted(command.postId());
        if (projection == null || projection.postId() == null) {
            searchApplicationService.deletePost(new SearchApplicationService.DeleteIndexedPostCommand(
                    command.postId(),
                    command.sourceVersion()
            ));
            return;
        }
        searchApplicationService.syncPostProjection(PostSearchPayloadAssembler.toSyncCommand(projection));
    }

    public record ProjectPostCommand(UUID postId, String sourceEventId, long sourceVersion) {
    }
}
