package com.nowcoder.community.content.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostContentBlockDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.PostContentBlockMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostContentBlockRepositoryJsonCodecTest {

    @Test
    void replaceBlocksShouldSerializeMetadataWithJsonCodec() {
        PostContentBlockMapper mapper = mock(PostContentBlockMapper.class);
        MyBatisPostContentBlockRepository repository = repository(mapper);
        UUID postId = uuid(101);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("width", 640);
        metadata.put("height", null);

        repository.replaceBlocks(postId, List.of(new PostContentBlock(
                uuid(201),
                postId,
                0,
                "image",
                "",
                uuid(301),
                "",
                "chart",
                "diagram.png",
                metadata
        )));

        ArgumentCaptor<PostContentBlockDataObject> rowCaptor = ArgumentCaptor.forClass(PostContentBlockDataObject.class);
        verify(mapper).insert(rowCaptor.capture());
        JsonNode metadataJson = jsonCodec().readTree(rowCaptor.getValue().getMetadataJson());
        assertThat(metadataJson.path("width").asInt()).isEqualTo(640);
        assertThat(metadataJson.has("height")).isTrue();
        assertThat(metadataJson.path("height").isNull()).isTrue();
    }

    @Test
    void listByPostIdShouldReadMetadataAsLinkedStringKeyMap() {
        PostContentBlockMapper mapper = mock(PostContentBlockMapper.class);
        MyBatisPostContentBlockRepository repository = repository(mapper);
        UUID postId = uuid(102);
        PostContentBlockDataObject row = row(postId, "{\"width\":640,\"layout\":\"wide\"}");
        when(mapper.selectByPostId(postId)).thenReturn(List.of(row));

        List<PostContentBlock> blocks = repository.listByPostId(postId);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).metadata())
                .containsExactly(
                        Map.entry("width", 640),
                        Map.entry("layout", "wide")
                );
        assertThatThrownBy(() -> blocks.get(0).metadata().put("mutated", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listByPostIdShouldWrapMalformedMetadataWithExistingBusinessMessage() {
        PostContentBlockMapper mapper = mock(PostContentBlockMapper.class);
        MyBatisPostContentBlockRepository repository = repository(mapper);
        UUID postId = uuid(103);
        when(mapper.selectByPostId(postId)).thenReturn(List.of(row(postId, "{")));

        assertThatThrownBy(() -> repository.listByPostId(postId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容块元数据反序列化失败");
    }

    private static MyBatisPostContentBlockRepository repository(PostContentBlockMapper mapper) {
        return new MyBatisPostContentBlockRepository(mapper, jsonCodec(), new UuidV7Generator());
    }

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }

    private static PostContentBlockDataObject row(UUID postId, String metadataJson) {
        PostContentBlockDataObject row = new PostContentBlockDataObject();
        row.setId(uuid(202));
        row.setPostId(postId);
        row.setBlockIndex(0);
        row.setBlockType("image");
        row.setTextContent("");
        row.setLanguage("");
        row.setMediaAssetId(uuid(302));
        row.setCaption("chart");
        row.setDisplayName("diagram.png");
        row.setMetadataJson(metadataJson);
        return row;
    }
}
