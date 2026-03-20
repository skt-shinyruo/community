package com.nowcoder.community.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地帖子扫描配置（reindex 扫描分页大小）。
 */
@Component
@ConfigurationProperties(prefix = "search.post-scan")
public class PostScanProperties {

    /**
     * 每次扫描拉取数量（默认 500，最大建议不超过 1000）。
     */
    private int pageSize = 500;

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
