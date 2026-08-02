package com.nowcoder.community.common.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private String accessPublicKey;
    private String accessPrivateKey;
    private String serviceHmacSecret;
    private String issuer;
    private String accessTokenAudience = "community-api";

    private long accessTokenTtlSeconds = 900;
    private long refreshTokenTtlSeconds = 604800;
    private long refreshReuseGraceSeconds = 10;

    private String refreshCookieName = "refresh_token";
    private String refreshCookiePath = "/api/auth";
    private String refreshCookieSameSite = "Lax";
    private boolean refreshCookieSecure = false;

    public String getAccessPublicKey() {
        return accessPublicKey;
    }

    public void setAccessPublicKey(String accessPublicKey) {
        this.accessPublicKey = accessPublicKey;
    }

    public String getAccessPrivateKey() {
        return accessPrivateKey;
    }

    public void setAccessPrivateKey(String accessPrivateKey) {
        this.accessPrivateKey = accessPrivateKey;
    }

    public String getServiceHmacSecret() {
        return serviceHmacSecret;
    }

    public void setServiceHmacSecret(String serviceHmacSecret) {
        this.serviceHmacSecret = serviceHmacSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAccessTokenAudience() {
        return accessTokenAudience;
    }

    public void setAccessTokenAudience(String accessTokenAudience) {
        this.accessTokenAudience = accessTokenAudience;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public long getRefreshReuseGraceSeconds() {
        return refreshReuseGraceSeconds;
    }

    public void setRefreshReuseGraceSeconds(long refreshReuseGraceSeconds) {
        this.refreshReuseGraceSeconds = refreshReuseGraceSeconds;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    public String getRefreshCookiePath() {
        return refreshCookiePath;
    }

    public void setRefreshCookiePath(String refreshCookiePath) {
        this.refreshCookiePath = refreshCookiePath;
    }

    public String getRefreshCookieSameSite() {
        return refreshCookieSameSite;
    }

    public void setRefreshCookieSameSite(String refreshCookieSameSite) {
        this.refreshCookieSameSite = refreshCookieSameSite;
    }

    public boolean isRefreshCookieSecure() {
        return refreshCookieSecure;
    }

    public void setRefreshCookieSecure(boolean refreshCookieSecure) {
        this.refreshCookieSecure = refreshCookieSecure;
    }
}
