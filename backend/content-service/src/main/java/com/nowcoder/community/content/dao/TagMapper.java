package com.nowcoder.community.content.dao;

import com.nowcoder.community.content.entity.HotTag;
import com.nowcoder.community.content.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagMapper {

    Tag selectTagByName(@Param("name") String name);

    int insertTag(Tag tag);

    /**
     * 标签建议：按前缀匹配并按使用次数排序（用于发帖端自动补全）。
     */
    List<HotTag> selectSuggestTags(@Param("q") String q, @Param("limit") int limit);

    List<HotTag> selectHotTags(@Param("limit") int limit);
}
