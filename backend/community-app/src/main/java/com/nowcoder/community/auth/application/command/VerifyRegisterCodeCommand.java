package com.nowcoder.community.auth.application.command;

public record VerifyRegisterCodeCommand(String registrationToken, String code) {
}
