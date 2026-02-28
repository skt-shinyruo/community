package com.nowcoder.community.user.api.dto;

public class InternalActivationResponse {

    /**
     * 0=success, 1=repeat, 2=failure（与旧单体/原 auth-service 语义对齐，方便迁移）
     */
    private int result;

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }
}

