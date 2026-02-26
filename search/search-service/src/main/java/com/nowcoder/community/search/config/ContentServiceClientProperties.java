package com.nowcoder.community.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * search-service -> content-service 内部调用配置（reindex 扫描分页大小）。
 *
 * <p>说明：服务间同步调用已迁移为 Dubbo RPC，因此不再需要 HTTP baseUrl/timeout 配置。</p>
 */
@Component
@ConfigurationProperties(prefix = "search.content-client")
public class ContentServiceClientProperties {

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
