package com.nowcoder.community.app.retirement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnusedSurfaceRetirementTest {

    @Test
    void retiredBackendSurfacesShouldNotRemainOnClasspath() {
        assertClassIsRetired("com.nowcoder.community.auth.application.command.VerifyCaptchaCommand");
        assertClassIsRetired("com.nowcoder.community.auth.controller.dto.CaptchaVerifyRequest");
        assertClassIsRetired("com.nowcoder.community.user.application.result.UserResolveResult");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.UserResolveResponse");
        assertClassIsRetired("com.nowcoder.community.analytics.controller.dto.RangeQuery");
        assertClassIsRetired("com.nowcoder.community.analytics.application.result.AnalyticsCountResult");
        assertClassIsRetired("com.nowcoder.community.search.application.result.ReindexJobResult");
        assertClassIsRetired("com.nowcoder.community.market.application.command.AdminResolveMarketDisputeCommand");
        assertClassIsRetired("com.nowcoder.community.market.application.command.MarketWalletActionCommand");
        assertClassIsRetired("com.nowcoder.community.auth.domain.model.AuthCredential");
        assertClassIsRetired("com.nowcoder.community.auth.domain.model.AuthTokens");
        assertClassIsRetired("com.nowcoder.community.auth.domain.model.CaptchaChallenge");
        assertClassIsRetired("com.nowcoder.community.auth.domain.model.PasswordResetToken");
        assertClassIsRetired("com.nowcoder.community.auth.domain.model.RefreshTokenRecord");
        assertClassIsRetired("com.nowcoder.community.auth.domain.model.RegistrationCode");
        assertClassIsRetired("com.nowcoder.community.auth.domain.model.RegistrationSession");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalActivationResponse");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalAuthenticateRequest");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalAuthenticateResponse");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalBatchUserSummaryRequest");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalModerationApplyRequest");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalModerationStatusResponse");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalRefreshTokenRecordResponse");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalRefreshTokenRevokeFamilyRequest");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalRefreshTokenRevokeRequest");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalRefreshTokenStoreRequest");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalRegisterRequest");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalSessionProfileResponse");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalUpdatePasswordRequest");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalUserByEmailResponse");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.InternalUserSummaryResponse");
        assertClassIsRetired("com.nowcoder.community.user.controller.dto.LeaderboardItemResponse");
        assertClassIsRetired("com.nowcoder.community.social.controller.dto.InternalUserProfileStatsResponse");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.dataobject.RewardAccountDataObject");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.dataobject.RewardGrantRecordDataObject");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.dataobject.RewardLedgerEntryDataObject");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.dataobject.UserTaskEventLogDataObject");
    }

    private void assertClassIsRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
