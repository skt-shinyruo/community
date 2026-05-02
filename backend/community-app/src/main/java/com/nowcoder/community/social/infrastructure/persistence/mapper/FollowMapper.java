package com.nowcoder.community.social.infrastructure.persistence.mapper;

import com.nowcoder.community.social.infrastructure.persistence.dataobject.FollowRelationDataObject;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mapper
public interface FollowMapper {

    @Insert("insert into social_follow(user_id, entity_type, entity_id, created_at) values(#{userId, jdbcType=BINARY}, #{entityType}, #{entityId, jdbcType=BINARY}, #{createdAt})")
    int insertFollow(
            @Param("userId") UUID userId,
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId,
            @Param("createdAt") Date createdAt
    );

    @Delete("delete from social_follow where user_id = #{userId, jdbcType=BINARY} and entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    int deleteFollow(@Param("userId") UUID userId, @Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Select("select count(1) from social_follow where user_id = #{userId, jdbcType=BINARY} and entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    int countFollow(@Param("userId") UUID userId, @Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Select("select count(1) from social_follow where user_id = #{userId, jdbcType=BINARY} and entity_type = #{entityType}")
    long countFollowees(@Param("userId") UUID userId, @Param("entityType") int entityType);

    @Select("select count(1) from social_follow where entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    long countFollowers(@Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Select("""
            select count(1)
            from social_follow f
            where f.user_id = #{userId, jdbcType=BINARY}
              and f.entity_type = #{entityType}
              and not exists (
                select 1 from social_block b
                where (b.user_id = #{userId, jdbcType=BINARY} and b.target_user_id = f.entity_id)
                   or (b.user_id = f.entity_id and b.target_user_id = #{userId, jdbcType=BINARY})
              )
            """)
    long countFolloweesExcludingBlocked(@Param("userId") UUID userId, @Param("entityType") int entityType);

    @Select("""
            select count(1)
            from social_follow f
            where f.entity_type = #{entityType}
              and f.entity_id = #{entityId, jdbcType=BINARY}
              and not exists (
                select 1 from social_block b
                where (b.user_id = #{entityId, jdbcType=BINARY} and b.target_user_id = f.user_id)
                   or (b.user_id = f.user_id and b.target_user_id = #{entityId, jdbcType=BINARY})
              )
            """)
    long countFollowersExcludingBlocked(@Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Select("select entity_id as targetId, created_at as followTime " +
            "from social_follow where user_id = #{userId} and entity_type = #{entityType} " +
            "order by created_at desc limit #{limit} offset #{offset}")
    List<FollowRelationDataObject> listFollowees(
            @Param("userId") UUID userId,
            @Param("entityType") int entityType,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    @Select("select user_id as targetId, created_at as followTime " +
            "from social_follow where entity_type = #{entityType} and entity_id = #{entityId} " +
            "order by created_at desc limit #{limit} offset #{offset}")
    List<FollowRelationDataObject> listFollowers(
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    @Select("""
            select f.entity_id as targetId, f.created_at as followTime
            from social_follow f
            where f.user_id = #{userId, jdbcType=BINARY}
              and f.entity_type = #{entityType}
              and not exists (
                select 1 from social_block b
                where (b.user_id = #{userId, jdbcType=BINARY} and b.target_user_id = f.entity_id)
                   or (b.user_id = f.entity_id and b.target_user_id = #{userId, jdbcType=BINARY})
              )
            order by f.created_at desc
            limit #{limit} offset #{offset}
            """)
    List<FollowRelationDataObject> listFolloweesExcludingBlocked(
            @Param("userId") UUID userId,
            @Param("entityType") int entityType,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    @Select("""
            select f.user_id as targetId, f.created_at as followTime
            from social_follow f
            where f.entity_type = #{entityType}
              and f.entity_id = #{entityId, jdbcType=BINARY}
              and not exists (
                select 1 from social_block b
                where (b.user_id = #{entityId, jdbcType=BINARY} and b.target_user_id = f.user_id)
                   or (b.user_id = f.user_id and b.target_user_id = #{entityId, jdbcType=BINARY})
              )
            order by f.created_at desc
            limit #{limit} offset #{offset}
            """)
    List<FollowRelationDataObject> listFollowersExcludingBlocked(
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}
