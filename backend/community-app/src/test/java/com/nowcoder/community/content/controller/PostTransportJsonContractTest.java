package com.nowcoder.community.content.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.application.result.PostContentBlockResult;
import com.nowcoder.community.content.application.result.PostMediaViewResult;
import com.nowcoder.community.content.controller.dto.CreatePostRequest;
import com.nowcoder.community.content.controller.dto.PostContentBlockRequest;
import com.nowcoder.community.content.controller.dto.UpdatePostRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class PostTransportJsonContractTest {

    private final ObjectMapper objectMapper = JsonMappers.standard();

    @Test
    void directBlockMediaModelShouldPreserveNestedResponseJsonFields() {
        PostMediaViewResult media = new PostMediaViewResult(
                uuid(41),
                "IMAGE",
                "UPLOADED",
                "NONE",
                "cover.png",
                "image/png",
                128L,
                "https://cdn.example/cover.png",
                "https://cdn.example/cover.png?download=1",
                "https://cdn.example/poster.png",
                List.of(new PostMediaViewResult.VideoSource(
                        "https://cdn.example/source.mp4", "video/mp4", 1280, 720
                ))
        );
        PostContentBlockResult block = new PostContentBlockResult(
                uuid(31),
                0,
                "image",
                "",
                media.assetId(),
                "",
                "cover",
                "cover.png",
                java.util.Map.of("width", 1280),
                media
        );

        JsonNode blockJson = objectMapper.valueToTree(block);

        assertThat(fieldNames(blockJson)).containsExactlyInAnyOrder(
                "id", "index", "type", "text", "assetId", "language", "caption",
                "displayName", "metadata", "media"
        );
        assertThat(fieldNames(blockJson.path("media"))).containsExactlyInAnyOrder(
                "assetId", "mediaKind", "lifecycle", "videoState", "fileName", "contentType",
                "contentLength", "url", "downloadUrl", "posterUrl", "sources"
        );
        assertThat(fieldNames(blockJson.path("media").path("sources").get(0)))
                .containsExactlyInAnyOrder("url", "contentType", "width", "height");
    }

    @Test
    void postRequestRecordsShouldKeepJsonShapeAndMetadataOrder() throws Exception {
        String json = """
                {
                  "title": "title",
                  "blocks": [{
                    "type": "image",
                    "text": "",
                    "assetId": "%s",
                    "language": "",
                    "caption": "cover",
                    "displayName": "cover.png",
                    "metadata": {"width": 1280, "height": null}
                  }],
                  "categoryId": "%s",
                  "tags": ["java"]
                }
                """.formatted(uuid(41), uuid(3));

        CreatePostRequest create = objectMapper.readValue(json, CreatePostRequest.class);
        UpdatePostRequest update = objectMapper.readValue(json, UpdatePostRequest.class);

        assertThat(create.title()).isEqualTo("title");
        assertThat(create.tags()).containsExactly("java");
        assertBlock(create.blocks().get(0));
        assertThat(update.title()).isEqualTo(create.title());
        assertThat(update.categoryId()).isEqualTo(create.categoryId());
        assertBlock(update.blocks().get(0));
    }

    private static void assertBlock(PostContentBlockRequest block) {
        assertThat(block.type()).isEqualTo("image");
        assertThat(block.caption()).isEqualTo("cover");
        assertThat(block.metadata().keySet()).containsExactly("width", "height");
        assertThat(block.metadata()).containsEntry("width", 1280).containsEntry("height", null);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
