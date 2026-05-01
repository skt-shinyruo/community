package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.Category;

import java.util.List;
import java.util.UUID;

public interface CategoryContentRepository {

    List<Category> listCategories();

    Category getById(UUID id);

    void assertExists(UUID categoryId);
}
