package com.nowcoder.community.auth.application.result;

import org.springframework.http.ResponseCookie;

public record RefreshResult(String accessToken, ResponseCookie refreshCookie) {
}
