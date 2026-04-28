package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.content.domain.model.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CategoryMapper {

    List<Category> selectCategories();

    Category selectCategoryById(@Param("id") UUID id);
}
