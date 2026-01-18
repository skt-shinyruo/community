package com.nowcoder.community.search.service;

import com.nowcoder.community.search.api.dto.SearchPostItem;
import com.nowcoder.community.search.repo.PostSearchRepository;
import com.nowcoder.community.common.event.payload.PostPayload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class PostSearchService {

    private final PostSearchRepository postSearchRepository;
    private final JdbcTemplate jdbcTemplate;

    public PostSearchService(PostSearchRepository postSearchRepository, JdbcTemplate jdbcTemplate) {
        this.postSearchRepository = postSearchRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SearchPostItem> search(String keyword, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        String k = StringUtils.hasText(keyword) ? keyword.trim() : "";
        return postSearchRepository.search(k, p, s);
    }

    public int clearAndReindexFromDb() {
        postSearchRepository.clear();
        String sql = "select id, user_id, title, content, type, status, create_time, score from discuss_post where status != 2";
        return jdbcTemplate.query(sql, rs -> {
            int count = 0;
            while (rs.next()) {
                PostPayload post = new PostPayload();
                post.setPostId(rs.getInt("id"));
                post.setUserId(rs.getInt("user_id"));
                post.setTitle(rs.getString("title"));
                post.setContent(rs.getString("content"));
                post.setType(rs.getInt("type"));
                post.setStatus(rs.getInt("status"));
                Timestamp ts = rs.getTimestamp("create_time");
                post.setCreateTime(ts == null ? null : ts.toInstant());
                double score = rs.getDouble("score");
                post.setScore(rs.wasNull() ? null : score);
                postSearchRepository.upsert(post);
                count++;
            }
            return count;
        });
    }
}
