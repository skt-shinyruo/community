package com.nowcoder.community.auth.application.result;

import org.springframework.http.ResponseCookie;

public record LoginResult(String accessToken, ResponseCookie refreshCookie) {
}
