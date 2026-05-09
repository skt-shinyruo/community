package com.nowcoder.community.content.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostContentBlockDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.PostContentBlockMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;

@Repository
public class MyBatisPostContentBlockRepository implements PostContentBlockRepository {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final PostContentBlockMapper mapper;
    private final ObjectMapper objectMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisPostContentBlockRepository(PostContentBlockMapper mapper, ObjectMapper objectMapper) {
        this(mapper, objectMapper, new UuidV7Generator());
    }

    MyBatisPostContentBlockRepository(PostContentBlockMapper mapper, ObjectMapper objectMapper, UuidV7Generator idGenerator) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public void replaceBlocks(UUID postId, List<PostContentBlock> blocks) {
        mapper.deleteByPostId(postId);
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (PostContentBlock block : blocks) {
            mapper.insert(toRow(postId, block, now));
        }
    }

    @Override
    public List<PostContentBlock> listByPostId(UUID postId) {
        if (postId == null) {
            return List.of();
        }
        return mapper.selectByPostId(postId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Map<UUID, List<PostContentBlock>> listByPostIds(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> cleanIds = postIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (cleanIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectByPostIds(cleanIds).stream()
                .map(this::toDomain)
                .collect(Collectors.groupingBy(
                        PostContentBlock::postId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private PostContentBlockDataObject toRow(UUID postId, PostContentBlock block, Date now) {
        PostContentBlockDataObject row = new PostContentBlockDataObject();
        row.setId(block.id() == null ? idGenerator.next() : block.id());
        row.setPostId(postId);
        row.setBlockIndex(block.index());
        row.setBlockType(block.type());
        row.setTextContent(block.text());
        row.setLanguage(block.language());
        row.setMediaAssetId(block.mediaAssetId());
        row.setCaption(block.caption());
        row.setDisplayName(block.displayName());
        row.setMetadataJson(writeMetadata(block.metadata()));
        row.setCreateTime(now);
        row.setUpdateTime(null);
        return row;
    }

    private PostContentBlock toDomain(PostContentBlockDataObject row) {
        return new PostContentBlock(
                row.getId(),
                row.getPostId(),
                row.getBlockIndex(),
                row.getBlockType(),
                row.getTextContent(),
                row.getMediaAssetId(),
                row.getLanguage(),
                row.getCaption(),
                row.getDisplayName(),
                readMetadata(row.getMetadataJson())
        );
    }

    private String writeMetadata(Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        if (safeMetadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(safeMetadata);
        } catch (JsonProcessingException e) {
            throw new BusinessException(INTERNAL_ERROR, "内容块元数据序列化失败", e);
        }
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, METADATA_TYPE);
            return metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        } catch (JsonProcessingException e) {
            throw new BusinessException(INTERNAL_ERROR, "内容块元数据反序列化失败", e);
        }
    }
}
