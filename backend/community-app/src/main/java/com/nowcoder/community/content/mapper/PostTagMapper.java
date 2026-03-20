package com.nowcoder.community.content.mapper;

import com.nowcoder.community.content.entity.PostTagName;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface PostTagMapper {

    int insertPostTag(@Param("postId") int postId, @Param("tagId") int tagId, @Param("createTime") Date createTime);

    List<PostTagName> selectTagNamesByPostIds(@Param("postIds") List<Integer> postIds);

    int deleteTagsByPostId(@Param("postId") int postId);
}
