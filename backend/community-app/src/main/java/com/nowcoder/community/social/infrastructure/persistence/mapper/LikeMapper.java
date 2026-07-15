package com.nowcoder.community.social.infrastructure.persistence.mapper;

import com.nowcoder.community.social.infrastructure.persistence.dataobject.EntityLikeCountDataObject;
import com.nowcoder.community.social.infrastructure.persistence.dataobject.LikeOwnerCountDataObject;
import com.nowcoder.community.social.infrastructure.persistence.dataobject.LikeScanDataObject;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;
import java.util.UUID;

@Mapper
public interface LikeMapper {

    @Insert("insert into social_like(user_id, entity_type, entity_id, entity_user_id, created_at) values(#{userId, jdbcType=BINARY}, #{entityType}, #{entityId, jdbcType=BINARY}, #{entityUserId, jdbcType=BINARY}, now())")
    int insertLike(@Param("userId") UUID userId, @Param("entityType") int entityType, @Param("entityId") UUID entityId, @Param("entityUserId") UUID entityUserId);

    @Select("select user_id as userId, entity_id as entityId, entity_user_id as entityUserId from social_like where user_id = #{userId, jdbcType=BINARY} and entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    LikeScanDataObject selectLike(@Param("userId") UUID userId, @Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Delete("delete from social_like where user_id = #{userId, jdbcType=BINARY} and entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    int deleteLike(@Param("userId") UUID userId, @Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Delete("delete from social_like where entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    int deleteLikesByEntity(@Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Select("select entity_user_id as entityUserId, count(1) as likeCount from social_like where entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY} and entity_user_id is not null group by entity_user_id")
    List<LikeOwnerCountDataObject> countLikeOwnersByEntity(@Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Select("select count(1) from social_like where user_id = #{userId, jdbcType=BINARY} and entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    int countLike(@Param("userId") UUID userId, @Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Select("select count(1) from social_like where entity_type = #{entityType} and entity_id = #{entityId, jdbcType=BINARY}")
    long countEntityLikes(@Param("entityType") int entityType, @Param("entityId") UUID entityId);

    @Insert("insert into social_user_like_count(user_id, like_count) values(#{userId}, greatest(0, #{delta})) " +
            "on duplicate key update like_count = greatest(0, like_count + #{delta})")
    int incrementUserLikeCount(@Param("userId") UUID userId, @Param("delta") long delta);

    @Insert("insert into social_user_like_count(user_id, like_count) values(#{userId}, #{likeCount}) " +
            "on duplicate key update like_count = #{likeCount}")
    int resetUserLikeCount(@Param("userId") UUID userId, @Param("likeCount") long likeCount);

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

    /**
     * internal 扫描 likes：用于下游投影 backfill（keyset pagination）。
     *
     * <p>返回按 (entity_id asc, user_id asc) 排序的边列表。</p>
     */
    @Select("""
            select entity_id as entityId, user_id as userId, entity_user_id as entityUserId
            from social_like
            where entity_type = #{entityType}
              and (entity_id > #{afterEntityId} or (entity_id = #{afterEntityId} and user_id > #{afterUserId}))
            order by entity_id asc, user_id asc
            limit #{limit}
            """)
    List<LikeScanDataObject> scanLikes(
            @Param("entityType") int entityType,
            @Param("afterEntityId") UUID afterEntityId,
            @Param("afterUserId") UUID afterUserId,
            @Param("limit") int limit
    );

    @Select("""
            select user_id as userId, entity_id as entityId, entity_user_id as entityUserId
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
            select distinct entity_id
            from social_like
            where entity_type = #{entityType}
              and entity_id > #{afterEntityId, jdbcType=BINARY}
            order by entity_id asc
            limit #{limit}
            """)
    List<UUID> scanTargetIdsAfter(
            @Param("entityType") int entityType,
            @Param("afterEntityId") UUID afterEntityId,
            @Param("limit") int limit
    );
}
