package com.nowcoder.community.auth.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User selectById(@Param("id") int id);

    User selectByName(@Param("username") String username);

    int updatePassword(@Param("id") int id, @Param("password") String password);
}

