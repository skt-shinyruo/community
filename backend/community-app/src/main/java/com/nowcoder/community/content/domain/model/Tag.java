package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public class Tag {

    private UUID id;
    private String name;
    private Date createTime;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
