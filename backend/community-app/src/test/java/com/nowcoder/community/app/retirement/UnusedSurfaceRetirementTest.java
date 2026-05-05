package com.nowcoder.community.app.retirement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnusedSurfaceRetirementTest {

    @Test
    void retiredBackendSurfacesShouldNotRemainOnClasspath() {
        assertClassIsRetired(cn("com.nowcoder.community.auth.application.command.", "VerifyCaptcha", "Command"));
        assertClassIsRetired(cn("com.nowcoder.community.auth.controller.dto.", "CaptchaVerify", "Request"));
        assertClassIsRetired(cn("com.nowcoder.community.user.application.result.", "UserResolve", "Result"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "UserResolve", "Response"));
        assertClassIsRetired(cn("com.nowcoder.community.analytics.controller.dto.", "Range", "Query"));
        assertClassIsRetired(cn("com.nowcoder.community.analytics.application.result.", "AnalyticsCount", "Result"));
        assertClassIsRetired(cn("com.nowcoder.community.search.application.result.", "ReindexJob", "Result"));
        assertClassIsRetired(cn("com.nowcoder.community.market.application.command.", "AdminResolveMarketDispute", "Command"));
        assertClassIsRetired(cn("com.nowcoder.community.market.application.command.", "MarketWalletAction", "Command"));
        assertClassIsRetired(cn("com.nowcoder.community.auth.domain.model.", "Auth", "Credential"));
        assertClassIsRetired(cn("com.nowcoder.community.auth.domain.model.", "Auth", "Tokens"));
        assertClassIsRetired(cn("com.nowcoder.community.auth.domain.model.", "Captcha", "Challenge"));
        assertClassIsRetired(cn("com.nowcoder.community.auth.domain.model.", "PasswordReset", "Token"));
        assertClassIsRetired(cn("com.nowcoder.community.auth.domain.model.", "RefreshToken", "Record"));
        assertClassIsRetired(cn("com.nowcoder.community.auth.domain.model.", "Registration", "Code"));
        assertClassIsRetired(cn("com.nowcoder.community.auth.domain.model.", "Registration", "Session"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalActivation", "Response"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalAuthenticate", "Request"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalAuthenticate", "Response"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalBatchUserSummary", "Request"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalModerationApply", "Request"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalModerationStatus", "Response"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalRefreshTokenRecord", "Response"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalRefreshTokenRevokeFamily", "Request"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalRefreshTokenRevoke", "Request"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalRefreshTokenStore", "Request"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalRegister", "Request"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalSessionProfile", "Response"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalUpdatePassword", "Request"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalUserByEmail", "Response"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "InternalUserSummary", "Response"));
        assertClassIsRetired(cn("com.nowcoder.community.user.controller.dto.", "LeaderboardItem", "Response"));
        assertClassIsRetired(cn("com.nowcoder.community.social.controller.dto.", "InternalUserProfileStats", "Response"));
        assertClassIsRetired(cn("com.nowcoder.community.growth.infrastructure.persistence.dataobject.", "RewardAccount", "DataObject"));
        assertClassIsRetired(cn("com.nowcoder.community.growth.infrastructure.persistence.dataobject.", "RewardGrantRecord", "DataObject"));
        assertClassIsRetired(cn("com.nowcoder.community.growth.infrastructure.persistence.dataobject.", "RewardLedgerEntry", "DataObject"));
        assertClassIsRetired(cn("com.nowcoder.community.growth.infrastructure.persistence.dataobject.", "UserTaskEventLog", "DataObject"));
    }

    private void assertClassIsRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private String cn(String... parts) {
        return String.join("", parts);
    }
}
