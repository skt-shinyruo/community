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
                    select user_id as userId, target_user_id as targetUserId, version as version
                    from social_block
                    where (user_id > #{afterUserId})
                       or (user_id = #{afterUserId} and target_user_id > #{afterTargetUserId})
                    order by user_id asc, target_user_id asc
                    limit #{limit}
                    """
    )
    List<BlockRelationDataObject> scanBlocks(
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
