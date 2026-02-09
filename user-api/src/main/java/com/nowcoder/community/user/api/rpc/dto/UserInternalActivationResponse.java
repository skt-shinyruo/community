package com.nowcoder.community.user.api.rpc.dto;

import java.io.Serializable;

public class UserInternalActivationResponse implements Serializable {

    private int result;

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }
}

