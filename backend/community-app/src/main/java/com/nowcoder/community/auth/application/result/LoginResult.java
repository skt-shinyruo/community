package com.nowcoder.community.auth.application.result;

public record LoginResult(String accessToken, RefreshCookieSpec refreshCookie) {
}
