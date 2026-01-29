package com.nowcoder.community.user.dao;

import com.nowcoder.community.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface UserMapper {

    User selectById(int id);

    User selectByName(String username);

    User selectByEmail(String email);

    int insertUser(User user);

    int updateStatus(int id, int status);

    int updateHeader(int id, String headerUrl);

    int updatePassword(int id, String password);

    int updateModerationUntil(int id, java.util.Date muteUntil, java.util.Date banUntil);

    /**
     * internal 扫描接口使用：按主键游标向后扫描用户的治理状态（用于投影回填/纠偏）。
     */
    List<User> selectModerationUsersAfterId(@Param("afterId") int afterId, @Param("limit") int limit);

    /**
     * internal 批量用户摘要：用于下游聚合接口避免 N+1 RPC。
     */
    List<User> selectUserSummariesByIds(@Param("ids") List<Integer> ids);

    int addScore(int id, int delta);

    List<User> selectTopByScore(int limit);
}
