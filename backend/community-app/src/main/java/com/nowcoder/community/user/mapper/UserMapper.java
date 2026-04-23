package com.nowcoder.community.user.mapper;

import com.nowcoder.community.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface UserMapper {

    User selectById(UUID id);

    User selectByName(String username);

    User selectByEmail(String email);

    int insertUser(User user);

    int deletePendingUserIfExpired(@Param("id") UUID id, @Param("status") int status, @Param("cutoff") java.util.Date cutoff);

    int deletePendingUser(@Param("id") UUID id, @Param("status") int status);

    int deleteExpiredPendingUsers(@Param("status") int status, @Param("cutoff") java.util.Date cutoff);

    int updateStatus(UUID id, int status);

    int updateHeader(UUID id, String headerUrl);

    int updatePassword(UUID id, String password);

    int updateType(@Param("id") UUID id, @Param("type") int type);

    int updateModerationUntil(UUID id, java.util.Date muteUntil, java.util.Date banUntil);

    /**
     * internal 扫描接口使用：按主键游标向后扫描用户的治理状态（用于投影回填/纠偏）。
     */
    List<User> selectModerationUsersAfterId(@Param("afterId") UUID afterId, @Param("limit") int limit);

    /**
     * internal 批量用户摘要：用于下游聚合接口避免 N+1 模块调用。
     */
    List<User> selectUserSummariesByIds(@Param("ids") List<UUID> ids);

    int addScore(UUID id, int delta);

    List<User> selectTopByScore(int limit);
}
