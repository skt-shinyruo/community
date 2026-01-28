package com.nowcoder.community.social.like;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface LikeMapper {

    @Insert("insert into social_like(user_id, entity_type, entity_id, created_at) values(#{userId}, #{entityType}, #{entityId}, now())")
    int insertLike(@Param("userId") int userId, @Param("entityType") int entityType, @Param("entityId") int entityId);

    @Delete("delete from social_like where user_id = #{userId} and entity_type = #{entityType} and entity_id = #{entityId}")
    int deleteLike(@Param("userId") int userId, @Param("entityType") int entityType, @Param("entityId") int entityId);

    @Select("select count(1) from social_like where user_id = #{userId} and entity_type = #{entityType} and entity_id = #{entityId}")
    int countLike(@Param("userId") int userId, @Param("entityType") int entityType, @Param("entityId") int entityId);

    @Select("select count(1) from social_like where entity_type = #{entityType} and entity_id = #{entityId}")
    long countEntityLikes(@Param("entityType") int entityType, @Param("entityId") int entityId);

    @Insert("insert into social_user_like_count(user_id, like_count) values(#{userId}, #{delta}) " +
            "on duplicate key update like_count = like_count + #{delta}")
    int incrementUserLikeCount(@Param("userId") int userId, @Param("delta") long delta);

    @Select("select like_count from social_user_like_count where user_id = #{userId}")
    Long getUserLikeCount(@Param("userId") int userId);
}
