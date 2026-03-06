package com.nowcoder.community.user.api.rpc.dto;

import java.io.Serializable;

public class UserInternalRegisterResponse implements Serializable {

    private int userId;
    private String activationCode;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getActivationCode() {
        return activationCode;
    }

    public void setActivationCode(String activationCode) {
        this.activationCode = activationCode;
    }
}

