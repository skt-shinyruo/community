package com.nowcoder.community.content.application.port;

import com.nowcoder.community.content.domain.model.Category;

import java.util.List;
import java.util.UUID;

public interface CategoryContentPort {

    List<Category> listCategories();

    Category getById(UUID id);

    void assertExists(UUID categoryId);
}
