package com.nowcoder.community.user.dto;

public class InternalActivationResponse {

    /**
     * 0=success, 1=repeat, 2=failure（与历史实现语义对齐，方便迁移）
     */
    private int result;

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }
}
