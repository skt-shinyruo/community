package com.nowcoder.community.user.dao;

import com.nowcoder.community.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
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

    int addScore(int id, int delta);

    List<User> selectTopByScore(int limit);
}
