package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import com.nowcoder.community.content.application.result.CategoryResult;
import com.nowcoder.community.content.domain.model.Category;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryApplicationService {

    private final CategoryContentRepository categoryContentPort;

    public CategoryApplicationService(CategoryContentRepository categoryContentPort) {
        this.categoryContentPort = categoryContentPort;
    }

    public List<CategoryResult> listCategories() {
        return categoryContentPort.listCategories().stream()
                .map(this::toResult)
                .toList();
    }

    private CategoryResult toResult(Category category) {
        return new CategoryResult(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getPosition(),
                category.getPostCount()
        );
    }
}
