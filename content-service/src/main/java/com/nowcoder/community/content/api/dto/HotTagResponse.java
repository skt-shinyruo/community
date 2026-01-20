package com.nowcoder.community.content.api.dto;

public class HotTagResponse {

    private String name;
    private long useCount;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getUseCount() {
        return useCount;
    }

    public void setUseCount(long useCount) {
        this.useCount = useCount;
    }
}

