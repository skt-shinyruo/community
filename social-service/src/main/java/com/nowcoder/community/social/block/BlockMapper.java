package com.nowcoder.community.social.block;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BlockMapper {

    @Insert("insert into social_block(user_id, target_user_id, created_at) values(#{userId}, #{targetUserId}, now())")
    int insertBlock(@Param("userId") int userId, @Param("targetUserId") int targetUserId);

    @Delete("delete from social_block where user_id = #{userId} and target_user_id = #{targetUserId}")
    int deleteBlock(@Param("userId") int userId, @Param("targetUserId") int targetUserId);

    @Select("select count(1) from social_block where user_id = #{userId} and target_user_id = #{targetUserId}")
    int countBlock(@Param("userId") int userId, @Param("targetUserId") int targetUserId);

    @Select("select target_user_id from social_block where user_id = #{userId} order by created_at desc")
    List<Integer> listBlockedUserIds(@Param("userId") int userId);
}

