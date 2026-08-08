package com.nowcoder.community.social.infrastructure.persistence.mapper;

import com.nowcoder.community.social.infrastructure.persistence.dataobject.BlockRelationDataObject;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface BlockMapper {

    @Insert(
            """
                    insert into social_user_pair_lock(first_user_id, second_user_id)
                    values(#{firstUserId, jdbcType=BINARY}, #{secondUserId, jdbcType=BINARY})
                    on duplicate key update first_user_id = values(first_user_id)
                    """
    )
    int ensureUserPairLock(
            @Param("firstUserId") UUID firstUserId,
            @Param("secondUserId") UUID secondUserId
    );

    @Select(
            """
                    select 1
                    from social_user_pair_lock
                    where first_user_id = #{firstUserId, jdbcType=BINARY}
                      and second_user_id = #{secondUserId, jdbcType=BINARY}
                    for update
                    """
    )
    Integer lockUserPair(
            @Param("firstUserId") UUID firstUserId,
            @Param("secondUserId") UUID secondUserId
    );

    @Insert("insert into social_block(user_id, target_user_id, created_at, version) values(#{userId, jdbcType=BINARY}, #{targetUserId, jdbcType=BINARY}, now(), #{version})")
    int insertBlock(@Param("userId") UUID userId, @Param("targetUserId") UUID targetUserId, @Param("version") long version);

    @Delete("delete from social_block where user_id = #{userId, jdbcType=BINARY} and target_user_id = #{targetUserId, jdbcType=BINARY}")
    int deleteBlock(@Param("userId") UUID userId, @Param("targetUserId") UUID targetUserId);

    @Select("select count(1) from social_block where user_id = #{userId, jdbcType=BINARY} and target_user_id = #{targetUserId, jdbcType=BINARY}")
    int countBlock(@Param("userId") UUID userId, @Param("targetUserId") UUID targetUserId);

    @Select("select target_user_id from social_block where user_id = #{userId, jdbcType=BINARY} order by created_at desc")
    List<UUID> listBlockedUserIds(@Param("userId") UUID userId);

    @Select(
            """
                    select history.user_id as userId,
                           history.target_user_id as targetUserId,
                           history.version as version
                    from social_block_version_log history
                    where ((history.user_id > #{afterUserId})
                       or (history.user_id = #{afterUserId} and history.target_user_id > #{afterTargetUserId}))
                      and history.version = (
                          select max(candidate.version)
                          from social_block_version_log candidate
                          where candidate.user_id = history.user_id
                            and candidate.target_user_id = history.target_user_id
                            and candidate.version <= #{snapshotVersion}
                      )
                      and history.active = true
                    order by history.user_id asc, history.target_user_id asc
                    limit #{limit}
                    """
    )
    List<BlockRelationDataObject> scanBlocksAtVersion(
            @Param("snapshotVersion") long snapshotVersion,
            @Param("afterUserId") UUID afterUserId,
            @Param("afterTargetUserId") UUID afterTargetUserId,
            @Param("limit") int limit
    );

    @Insert(
            """
                    insert into social_block_version_log(version, user_id, target_user_id, active, occurred_at)
                    values(#{version}, #{userId, jdbcType=BINARY}, #{targetUserId, jdbcType=BINARY}, #{active}, now())
                    """
    )
    int insertVersionLog(
            @Param("version") long version,
            @Param("userId") UUID userId,
            @Param("targetUserId") UUID targetUserId,
            @Param("active") boolean active
    );

    @Insert("insert into social_block_version_counter(id, current_version) values(#{id}, 0) on duplicate key update current_version = current_version")
    int upsertVersionCounter(@Param("id") int id);

    @Select("select current_version from social_block_version_counter where id = #{id} for update")
    long selectVersionCounterForUpdate(@Param("id") int id);

    @org.apache.ibatis.annotations.Update("update social_block_version_counter set current_version = #{version} where id = #{id}")
    int updateVersionCounter(@Param("id") int id, @Param("version") long version);

    @Select("select coalesce(max(current_version), 0) from social_block_version_counter where id = #{id}")
    long selectVersionCounter(@Param("id") int id);
}
