package com.nowcoder.community.social.projection;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

@Mapper
public interface ContentEntityProjectionMapper {

    @Select("""
            select entity_type as entityType,
                   entity_id as entityId,
                   entity_user_id as entityUserId,
                   post_id as postId,
                   status as status,
                   updated_at as updatedAt
            from social_content_entity_projection
            where entity_type = #{entityType}
              and entity_id = #{entityId}
            """)
    ContentEntityProjection find(@Param("entityType") int entityType, @Param("entityId") long entityId);

    /**
     * 幂等 upsert（抗重复/乱序）：
     * - 若 incoming.updatedAt 更“新”，则覆盖 entity_user_id/post_id/status/updated_at
     * - 若 incoming 更“旧”，则忽略更新，避免旧事件回滚状态
     */
    @Insert("""
            insert into social_content_entity_projection(entity_type, entity_id, entity_user_id, post_id, status, updated_at)
            values(#{entityType}, #{entityId}, #{entityUserId}, #{postId}, #{status}, #{updatedAt})
            on duplicate key update
              entity_user_id = if(#{updatedAt} >= updated_at, #{entityUserId}, entity_user_id),
              post_id = if(#{updatedAt} >= updated_at, #{postId}, post_id),
              status = if(#{updatedAt} >= updated_at, #{status}, status),
              updated_at = if(#{updatedAt} >= updated_at, #{updatedAt}, updated_at)
            """)
    int upsertIfNewer(
            @Param("entityType") int entityType,
            @Param("entityId") long entityId,
            @Param("entityUserId") long entityUserId,
            @Param("postId") long postId,
            @Param("status") int status,
            @Param("updatedAt") Date updatedAt
    );
}

