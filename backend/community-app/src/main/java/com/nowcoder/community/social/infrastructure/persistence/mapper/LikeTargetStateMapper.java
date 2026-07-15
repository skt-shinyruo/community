package com.nowcoder.community.social.infrastructure.persistence.mapper;

import com.nowcoder.community.social.infrastructure.persistence.dataobject.LikeTargetStateDataObject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface LikeTargetStateMapper {

    @Insert("""
            insert into social_like_target_state(
                entity_type, entity_id, status, source_event_id, source_version, deleted_at, updated_at
            ) values (
                #{entityType}, #{entityId, jdbcType=BINARY}, 'ACTIVE', null, 0, null, current_timestamp
            )
            """)
    int insertActive(@Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Select("""
            select entity_type as entityType,
                   entity_id as entityId,
                   status,
                   source_event_id as sourceEventId,
                   source_version as sourceVersion,
                   deleted_at as deletedAt
            from social_like_target_state
            where entity_type = #{entityType}
              and entity_id = #{entityId, jdbcType=BINARY}
            """)
    LikeTargetStateDataObject selectByTarget(
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId
    );

    @Select("""
            select entity_type as entityType,
                   entity_id as entityId,
                   status,
                   source_event_id as sourceEventId,
                   source_version as sourceVersion,
                   deleted_at as deletedAt
            from social_like_target_state
            where entity_type = #{entityType}
              and entity_id = #{entityId, jdbcType=BINARY}
            for update
            """)
    LikeTargetStateDataObject selectForUpdate(
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId
    );

    @Update("""
            update social_like_target_state
            set status = 'DELETED',
                source_event_id = #{sourceEventId},
                source_version = #{sourceVersion},
                deleted_at = #{deletedAt},
                updated_at = current_timestamp
            where entity_type = #{entityType}
              and entity_id = #{entityId, jdbcType=BINARY}
              and source_version < #{sourceVersion}
            """)
    int updateDeletedIfNewer(
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId,
            @Param("sourceEventId") String sourceEventId,
            @Param("sourceVersion") long sourceVersion,
            @Param("deletedAt") Instant deletedAt
    );

    @Select("""
            select target_state.entity_type as entityType,
                   target_state.entity_id as entityId,
                   target_state.status,
                   target_state.source_event_id as sourceEventId,
                   target_state.source_version as sourceVersion,
                   target_state.deleted_at as deletedAt
            from social_like_target_state target_state
            where target_state.entity_type = #{entityType}
              and target_state.status = 'DELETED'
              and target_state.entity_id > #{afterEntityId, jdbcType=BINARY}
              and exists (
                  select 1
                  from social_like likes
                  where likes.entity_type = target_state.entity_type
                    and likes.entity_id = target_state.entity_id
              )
            order by target_state.entity_id asc
            limit #{limit}
            """)
    List<LikeTargetStateDataObject> scanDeletedTargetsWithLikesAfter(
            @Param("entityType") int entityType,
            @Param("afterEntityId") UUID afterEntityId,
            @Param("limit") int limit
    );
}
