package com.nowcoder.community.content.mapper;

import com.nowcoder.community.content.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper {

    List<Category> selectCategories();

    Category selectCategoryById(@Param("id") int id);
}

