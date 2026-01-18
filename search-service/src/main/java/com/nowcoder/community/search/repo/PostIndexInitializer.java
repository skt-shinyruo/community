package com.nowcoder.community.search.repo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
public class PostIndexInitializer implements ApplicationRunner {

    private final ElasticsearchOperations operations;

    public PostIndexInitializer(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public void run(ApplicationArguments args) {
        var indexOps = operations.indexOps(EsPostDocument.class);
        if (!indexOps.exists()) {
            indexOps.createWithMapping();
        }
    }
}

