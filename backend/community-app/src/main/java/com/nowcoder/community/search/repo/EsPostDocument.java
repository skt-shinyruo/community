package com.nowcoder.community.search.repo;

// ES 帖子索引文档：通过固定 alias 访问，真实索引使用版本号区分。
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

@Document(indexName = EsPostDocument.INDEX_ALIAS)
public class EsPostDocument {

    public static final String INDEX_ALIAS = "community_posts_alias";
    public static final String LEGACY_INDEX = "community_posts";
    public static final String INDEX_PREFIX = "community_posts_v";

    @Id
    private Integer postId;

    private Integer userId;
    private Integer categoryId;

    @Field(type = FieldType.Keyword)
    private List<String> tags;
    private String title;
    private String content;
    private Integer type;
    private Integer status;
    /**
     * 存储为 epoch millis，避免 Spring Data Elasticsearch 对 Instant 的读写转换不一致导致查询报错。
     */
    private Long createTime;
    private Double score;

    public Integer getPostId() {
        return postId;
    }

    public void setPostId(Integer postId) {
        this.postId = postId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
