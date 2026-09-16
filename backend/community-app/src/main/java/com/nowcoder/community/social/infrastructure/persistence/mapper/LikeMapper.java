package com.nowcoder.community.social.infrastructure.persistence.mapper;

import com.nowcoder.community.social.infrastructure.persistence.dataobject.EntityLikeCountDataObject;
import com.nowcoder.community.social.infrastructure.persistence.dataobject.LikeScanDataObject;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

@Mapper
public interface LikeMapper {

    @Insert("insert into social_like(relation_instance_id, user_id, entity_type, entity_id, entity_user_id, post_id, created_at) values(#{relationInstanceId, jdbcType=BINARY}, #{userId, jdbcType=BINARY}, #{entityType}, #{entityId, jdbcType=BINARY}, #{entityUserId, jdbcType=BINARY}, #{postId, jdbcType=BINARY}, now())")
    int insertLike(
            @Param("relationInstanceId") UUID relationInstanceId,
            @Param("userId") UUID userId,
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId,
            @Param("entityUserId") UUID entityUserId,
            @Param("postId") UUID postId
    );

    @Select("select relation_instance_id as relationInstanceId, user_id as userId, entity_id as entityId, entity_user_id as entityUserId, post_id as postId from social_like where user_id = #{userId, jdbcType=BINARY} and entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    LikeScanDataObject selectLike(@Param("userId") UUID userId, @Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Delete("delete from social_like where user_id = #{userId, jdbcType=BINARY} and entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY} and relation_instance_id = #{relationInstanceId, jdbcType=BINARY}")
    int deleteLike(
            @Param("userId") UUID userId,
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId,
            @Param("relationInstanceId") UUID relationInstanceId
    );

    @Insert("""
            insert into social_like_relation_version(
                actor_user_id, entity_type, entity_id, current_version, updated_at
            )
            values(
                #{actorUserId, jdbcType=BINARY}, #{entityType}, #{entityId, jdbcType=BINARY},
                4611686018427387904, now()
            )
            on duplicate key update actor_user_id = values(actor_user_id)
            """)
    int ensureRelationEventVersion(
            @Param("actorUserId") UUID actorUserId,
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId
    );

    @Select("""
            select current_version
            from social_like_relation_version
            where actor_user_id = #{actorUserId, jdbcType=BINARY}
              and entity_type = #{entityType}
              and entity_id = #{entityId, jdbcType=BINARY}
            for update
            """)
    long selectRelationEventVersionForUpdate(
            @Param("actorUserId") UUID actorUserId,
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId
    );

    @Update("""
            update social_like_relation_version
            set current_version = #{nextVersion},
                updated_at = now()
            where actor_user_id = #{actorUserId, jdbcType=BINARY}
              and entity_type = #{entityType}
              and entity_id = #{entityId, jdbcType=BINARY}
              and current_version = #{currentVersion}
            """)
    int updateRelationEventVersion(
            @Param("actorUserId") UUID actorUserId,
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId,
            @Param("currentVersion") long currentVersion,
            @Param("nextVersion") long nextVersion
    );

    @Select("select count(1) from social_like where user_id = #{userId, jdbcType=BINARY} and entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    int countLike(@Param("userId") UUID userId, @Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Select("select count(1) from social_like where entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    long countEntityLikes(@Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Insert("insert into social_user_like_count(user_id, like_count) values(#{userId}, greatest(0, #{delta})) " +
            "on duplicate key update like_count = greatest(0, like_count + #{delta})")
    int incrementUserLikeCount(@Param("userId") UUID userId, @Param("delta") long delta);

    @Select("select like_count from social_user_like_count where user_id = #{userId}")
    Long getUserLikeCount(@Param("userId") UUID userId);

    @Select("""
            <script>
            select entity_id as entityId, count(1) as likeCount
            from social_like
            where entity_type = #{entityType}
              and entity_id in
              <foreach collection="entityIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            group by entity_id
            </script>
            """)
    List<EntityLikeCountDataObject> countEntityLikesByEntityIds(@Param("entityType") int entityType, @Param("entityIds") List<UUID> entityIds);

    @Select("""
            <script>
            select entity_id
            from social_like
            where user_id = #{userId}
              and entity_type = #{entityType}
              and entity_id in
              <foreach collection="entityIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    List<UUID> selectLikedEntityIds(@Param("userId") UUID userId, @Param("entityType") int entityType, @Param("entityIds") List<UUID> entityIds);

    @Select("""
            select relation_instance_id as relationInstanceId,
                   user_id as userId,
                   entity_id as entityId,
                   entity_user_id as entityUserId,
                   post_id as postId
            from social_like
            where entity_type = #{entityType}
              and entity_id = #{entityId, jdbcType=BINARY}
              and user_id > #{afterUserId, jdbcType=BINARY}
            order by user_id asc
            limit #{limit}
            """)
    List<LikeScanDataObject> scanLikesByEntity(
            @Param("entityType") int entityType,
            @Param("entityId") UUID entityId,
            @Param("afterUserId") UUID afterUserId,
            @Param("limit") int limit
    );

    @Select("""
            select relation_instance_id as relationInstanceId,
                   user_id as userId,
                   entity_id as entityId,
                   entity_user_id as entityUserId,
                   post_id as postId
            from social_like
            where entity_type = #{commentEntityType}
              and post_id = #{postId, jdbcType=BINARY}
              and (
                  entity_id > #{afterCommentId, jdbcType=BINARY}
                  or (
                      entity_id = #{afterCommentId, jdbcType=BINARY}
                      and user_id > #{afterUserId, jdbcType=BINARY}
                  )
              )
            order by entity_id asc, user_id asc
            limit #{limit}
            """)
    List<LikeScanDataObject> scanCommentLikesByPost(
            @Param("commentEntityType") int commentEntityType,
            @Param("postId") UUID postId,
            @Param("afterCommentId") UUID afterCommentId,
            @Param("afterUserId") UUID afterUserId,
            @Param("limit") int limit
    );
}
