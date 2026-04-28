package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.repository.PostTagRepository;
import com.nowcoder.community.content.domain.model.Tag;
import com.nowcoder.community.content.infrastructure.persistence.mapper.PostTagMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.TagMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Repository
public class MyBatisPostTagRepository implements PostTagRepository {

    private static final int MAX_TAGS_PER_POST = 5;
    private static final int MAX_TAG_LEN = 20;
    private static final Pattern TAG_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_\\-]{1," + MAX_TAG_LEN + "}$");

    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisPostTagRepository(TagMapper tagMapper, PostTagMapper postTagMapper) {
        this(tagMapper, postTagMapper, new UuidV7Generator());
    }

    MyBatisPostTagRepository(TagMapper tagMapper, PostTagMapper postTagMapper, UuidV7Generator idGenerator) {
        this.tagMapper = tagMapper;
        this.postTagMapper = postTagMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<String> bindTagsToPost(UUID postId, List<String> rawTags) {
        if (postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId 非法");
        }
        List<String> tags = normalizeTags(rawTags);
        if (tags.isEmpty()) {
            return List.of();
        }

        Date now = new Date();
        for (String name : tags) {
            UUID tagId = ensureTagId(name, now);
            try {
                postTagMapper.insertPostTag(postId, tagId, now);
            } catch (DuplicateKeyException ignored) {
                // normalizeTags 已去重；这里兜底处理并发重复绑定。
            }
        }
        return tags;
    }

    @Override
    public List<String> replaceTagsForPost(UUID postId, List<String> rawTags) {
        if (postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId 非法");
        }
        postTagMapper.deleteTagsByPostId(postId);
        return bindTagsToPost(postId, rawTags);
    }

    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }

        List<String> parts = new ArrayList<>();
        for (String raw : rawTags) {
            if (raw == null) {
                continue;
            }
            String s = raw.trim();
            if (s.isEmpty()) {
                continue;
            }
            String[] split = s.split("[\\s,，]+");
            for (String x : split) {
                if (x == null) {
                    continue;
                }
                String t = x.trim();
                if (t.startsWith("#")) {
                    t = t.substring(1).trim();
                }
                if (!t.isEmpty()) {
                    parts.add(t);
                }
            }
        }

        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String part : parts) {
            String tag = part.replaceAll("\\s+", "-").trim();
            if (tag.isEmpty()) {
                continue;
            }
            if (tag.length() > MAX_TAG_LEN) {
                throw new BusinessException(INVALID_ARGUMENT, "标签过长（单个标签最长 " + MAX_TAG_LEN + "）");
            }
            if (!TAG_PATTERN.matcher(tag).matches()) {
                throw new BusinessException(INVALID_ARGUMENT, "标签格式非法（仅允许中英文、数字、_、-）");
            }
            String key = tag.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                normalized.add(tag);
            }
            if (normalized.size() > MAX_TAGS_PER_POST) {
                throw new BusinessException(INVALID_ARGUMENT, "标签数量过多（最多 " + MAX_TAGS_PER_POST + " 个）");
            }
        }
        return normalized;
    }

    private UUID ensureTagId(String name, Date now) {
        Tag existed = tagMapper.selectTagByName(name);
        if (existed != null) {
            return existed.getId();
        }

        Tag tag = new Tag();
        tag.setId(idGenerator.next());
        tag.setName(name);
        tag.setCreateTime(now);

        try {
            tagMapper.insertTag(tag);
            return tag.getId();
        } catch (DuplicateKeyException ignored) {
            Tag retry = tagMapper.selectTagByName(name);
            if (retry != null) {
                return retry.getId();
            }
            throw new BusinessException(INVALID_ARGUMENT, "标签写入失败");
        }
    }
}
