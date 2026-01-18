package com.nowcoder.community.dao;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository("Mybatis")
@Primary
public class AlphaDaoMybatisImpl implements AlphaDao {


    @Override
    public String select() {
        return "hello Mybatis!";
    }
}
