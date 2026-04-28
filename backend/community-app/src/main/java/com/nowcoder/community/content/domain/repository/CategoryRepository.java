package com.nowcoder.community.content.domain.repository;

import java.util.UUID;

public interface CategoryRepository {

    void assertExists(UUID categoryId);
}
