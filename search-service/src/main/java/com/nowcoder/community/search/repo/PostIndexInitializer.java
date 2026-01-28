package com.nowcoder.community.search.repo;

// 启动期索引初始化：确保 alias 已就绪并指向可写索引。
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class PostIndexInitializer implements ApplicationRunner {

    private final PostIndexManager postIndexManager;

    public PostIndexInitializer(PostIndexManager postIndexManager) {
        this.postIndexManager = postIndexManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        postIndexManager.ensureAliasReady();
    }
}
