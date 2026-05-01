package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.content.domain.model.HotTag;
import com.nowcoder.community.content.domain.model.PostTagName;
import com.nowcoder.community.content.domain.model.Tag;
import com.nowcoder.community.content.infrastructure.persistence.mapper.PostTagMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.TagMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class MyBatisTagContentRepository implements TagContentRepository {

    private static final int DEFAULT_HOT_TAG_LIMIT = 8;
    private static final int MAX_HOT_TAG_LIMIT = 20;

    private static final int MAX_TAGS_PER_POST = 5;
    private static final int MAX_TAG_LEN = 20;
    private static final Pattern TAG_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_\\-]{1," + MAX_TAG_LEN + "}$");

    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisTagContentRepository(TagMapper tagMapper, PostTagMapper postTagMapper) {
        this(tagMapper, postTagMapper, new UuidV7Generator());
    }

    MyBatisTagContentRepository(TagMapper tagMapper, PostTagMapper postTagMapper, UuidV7Generator idGenerator) {
        this.tagMapper = tagMapper;
        this.postTagMapper = postTagMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<HotTag> listHotTags(Integer limit) {
        int l = limit == null ? DEFAULT_HOT_TAG_LIMIT : limit;
        l = Math.max(1, Math.min(MAX_HOT_TAG_LIMIT, l));
        return tagMapper.selectHotTags(l);
    }

    @Override
    public List<HotTag> suggestTags(String q, Integer limit) {
        int l = limit == null ? DEFAULT_HOT_TAG_LIMIT : limit;
        l = Math.max(1, Math.min(MAX_HOT_TAG_LIMIT, l));

        String keyword = q == null ? "" : q.trim();
        if (keyword.isBlank()) {
            return listHotTags(l);
        }

        List<HotTag> base = tagMapper.selectSuggestTags(keyword, l);
        if (base == null || base.isEmpty()) {
            return listHotTags(l);
        }
        if (base.size() >= l) {
            return base;
        }

        // 不足时用 hot tags 补齐（去重）
        Set<String> seen = new HashSet<>();
        for (HotTag t : base) {
            if (t != null && t.getName() != null) {
                seen.add(t.getName().toLowerCase(Locale.ROOT));
            }
        }
        List<HotTag> merged = new ArrayList<>(base);
        for (HotTag hot : listHotTags(l)) {
            if (hot == null || hot.getName() == null) {
                continue;
            }
            String key = hot.getName().toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                merged.add(hot);
            }
            if (merged.size() >= l) {
                break;
            }
        }
        return merged;
    }

    @Override
    public Map<UUID, List<String>> getTagsByPostIds(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<PostTagName> rows = postTagMapper.selectTagNamesByPostIds(postIds);
        Map<UUID, List<String>> map = new HashMap<>();
        for (PostTagName row : rows) {
            map.computeIfAbsent(row.getPostId(), k -> new ArrayList<>()).add(row.getName());
        }
        return map;
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
                // 去重：理论上已在 normalizeTags 处理；此处兜底并忽略重复绑定。
            }
        }
        return tags;
    }

    @Override
    public List<String> replaceTagsForPost(UUID postId, List<String> rawTags) {
        if (postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId 非法");
        }
        // 先删除再绑定：MVP 采用“全量替换”，后续可优化为 diff 更新。
        postTagMapper.deleteTagsByPostId(postId);
        return bindTagsToPost(postId, rawTags);
    }

    @Override
    public List<String> normalizeTags(List<String> rawTags) {
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
            // 允许前端传入单个字符串（逗号/空格分隔），后端做一次容错拆分。
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
        for (String p : parts) {
            String t = p.replaceAll("\\s+", "-").trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.length() > MAX_TAG_LEN) {
                throw new BusinessException(INVALID_ARGUMENT, "标签过长（单个标签最长 " + MAX_TAG_LEN + "）");
            }
            if (!TAG_PATTERN.matcher(t).matches()) {
                throw new BusinessException(INVALID_ARGUMENT, "标签格式非法（仅允许中英文、数字、_、-）");
            }
            String key = t.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                normalized.add(t);
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
