package com.nowcoder.community.auth.application.result;

public record RefreshResult(String accessToken, RefreshCookieSpec refreshCookie) {
}
