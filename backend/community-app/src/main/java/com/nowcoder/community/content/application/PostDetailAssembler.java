package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostContentBlockResult;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostMediaViewResult;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PostDetailAssembler {

    private final ContentTextCodec textCodec;

    public PostDetailAssembler(ContentTextCodec textCodec) {
        this.textCodec = textCodec;
    }

    public PostDetailResult assemble(
            DiscussPost post,
            List<PostContentBlock> blocks,
            List<PostMediaAsset> mediaAssets,
            List<String> tags,
            long likeCount,
            boolean liked,
            boolean bookmarked
    ) {
        List<String> safeTags = tags == null ? List.of() : List.copyOf(tags);
        Map<UUID, PostMediaAsset> mediaById = mediaAssets == null
                ? Map.of()
                : mediaAssets.stream()
                .filter(asset -> asset != null && asset.id() != null)
                .collect(Collectors.toMap(PostMediaAsset::id, Function.identity(), (left, right) -> left));
        return new PostDetailResult(
                post.getId(),
                post.getUserId(),
                textCodec.decodeOnRead(post.getTitle()),
                toBlockResults(blocks, mediaById),
                post.getType(),
                post.getStatus(),
                post.getCreateTime(),
                post.getUpdateTime(),
                post.getEditCount(),
                post.getCommentCount(),
                post.getScore(),
                post.getCategoryId(),
                safeTags,
                likeCount,
                liked,
                bookmarked
        );
    }

    private List<PostContentBlockResult> toBlockResults(List<PostContentBlock> blocks, Map<UUID, PostMediaAsset> mediaById) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
                .map(block -> new PostContentBlockResult(
                        block.id(),
                        block.index(),
                        block.type(),
                        textCodec.decodeOnRead(block.text()),
                        block.mediaAssetId(),
                        block.language(),
                        textCodec.decodeOnRead(block.caption()),
                        block.displayName(),
                        block.metadata(),
                        toMediaView(mediaById.get(block.mediaAssetId()))
                ))
                .toList();
    }

    private PostMediaViewResult toMediaView(PostMediaAsset asset) {
        if (asset == null) {
            return null;
        }
        String kind = asset.mediaKind() == null ? "" : asset.mediaKind().name();
        String publicUrl = asset.publicUrl() == null ? "" : asset.publicUrl();
        return new PostMediaViewResult(
                asset.id(),
                kind,
                asset.lifecycle() == null ? "" : asset.lifecycle().name(),
                asset.videoState() == null ? "" : asset.videoState().name(),
                asset.fileName(),
                asset.contentType(),
                asset.contentLength(),
                "IMAGE".equals(kind) ? publicUrl : "",
                publicUrl,
                "",
                List.of()
        );
    }
}
