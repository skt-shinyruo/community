package com.nowcoder.community.social.block;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface BlockMapper {

    @Insert("insert into social_block(user_id, target_user_id, created_at) values(#{userId, jdbcType=BINARY}, #{targetUserId, jdbcType=BINARY}, now())")
    int insertBlock(@Param("userId") UUID userId, @Param("targetUserId") UUID targetUserId);

    @Delete("delete from social_block where user_id = #{userId, jdbcType=BINARY} and target_user_id = #{targetUserId, jdbcType=BINARY}")
    int deleteBlock(@Param("userId") UUID userId, @Param("targetUserId") UUID targetUserId);

    @Select("select count(1) from social_block where user_id = #{userId, jdbcType=BINARY} and target_user_id = #{targetUserId, jdbcType=BINARY}")
    int countBlock(@Param("userId") UUID userId, @Param("targetUserId") UUID targetUserId);

    @Select("select target_user_id from social_block where user_id = #{userId, jdbcType=BINARY} order by created_at desc")
    List<UUID> listBlockedUserIds(@Param("userId") UUID userId);

    @Select(
            """
                    select user_id as userId, target_user_id as targetUserId
                    from social_block
                    where (user_id > #{afterUserId})
                       or (user_id = #{afterUserId} and target_user_id > #{afterTargetUserId})
                    order by user_id asc, target_user_id asc
                    limit #{limit}
                    """
    )
    List<BlockScanRow> scanBlocks(
            @Param("afterUserId") UUID afterUserId,
            @Param("afterTargetUserId") UUID afterTargetUserId,
            @Param("limit") int limit
    );
}
