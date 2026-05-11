package com.nowcoder.community.app.config;

import com.nowcoder.community.analytics.domain.service.AnalyticsDomainService;
import com.nowcoder.community.analytics.domain.service.AnalyticsIngestDomainService;
import com.nowcoder.community.auth.domain.service.AuthDomainService;
import com.nowcoder.community.auth.domain.service.CaptchaDomainService;
import com.nowcoder.community.auth.domain.service.LoginRateLimitDomainService;
import com.nowcoder.community.auth.domain.service.PasswordResetDomainService;
import com.nowcoder.community.auth.domain.service.RefreshTokenDomainService;
import com.nowcoder.community.auth.domain.service.RegistrationDomainService;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.content.domain.service.ModerationDecisionDomainService;
import com.nowcoder.community.content.domain.service.PostContentBlockPolicy;
import com.nowcoder.community.content.domain.service.PostModerationDomainService;
import com.nowcoder.community.content.domain.service.PostPublishingDomainService;
import com.nowcoder.community.search.domain.service.PostSearchDomainService;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.FollowDomainService;
import com.nowcoder.community.social.domain.service.LikeDomainService;
import com.nowcoder.community.user.domain.service.UserCredentialDomainService;
import com.nowcoder.community.user.domain.service.PasswordPolicyDomainService;
import com.nowcoder.community.user.domain.service.UserModerationDomainService;
import com.nowcoder.community.user.domain.service.UserReadDomainService;
import com.nowcoder.community.user.domain.service.UserRegistrationDomainService;
import com.nowcoder.community.user.domain.service.UserRoleDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    AnalyticsDomainService analyticsDomainService() {
        return new AnalyticsDomainService();
    }

    @Bean
    AnalyticsIngestDomainService analyticsIngestDomainService() {
        return new AnalyticsIngestDomainService();
    }

    @Bean
    AuthDomainService authDomainService() {
        return new AuthDomainService();
    }

    @Bean
    CaptchaDomainService captchaDomainService() {
        return new CaptchaDomainService();
    }

    @Bean
    LoginRateLimitDomainService loginRateLimitDomainService() {
        return new LoginRateLimitDomainService();
    }

    @Bean
    PasswordResetDomainService passwordResetDomainService() {
        return new PasswordResetDomainService();
    }

    @Bean
    RefreshTokenDomainService refreshTokenDomainService() {
        return new RefreshTokenDomainService();
    }

    @Bean
    RegistrationDomainService registrationDomainService() {
        return new RegistrationDomainService();
    }

    @Bean
    CommentDomainService commentDomainService() {
        return new CommentDomainService();
    }

    @Bean
    ModerationDecisionDomainService moderationDecisionDomainService() {
        return new ModerationDecisionDomainService();
    }

    @Bean
    PostModerationDomainService postModerationDomainService() {
        return new PostModerationDomainService();
    }

    @Bean
    PostPublishingDomainService postPublishingDomainService() {
        return new PostPublishingDomainService();
    }

    @Bean
    PostContentBlockPolicy postContentBlockPolicy() {
        return new PostContentBlockPolicy();
    }

    @Bean
    PostSearchDomainService postSearchDomainService() {
        return new PostSearchDomainService();
    }

    @Bean
    BlockDomainService blockDomainService() {
        return new BlockDomainService();
    }

    @Bean
    FollowDomainService followDomainService() {
        return new FollowDomainService();
    }

    @Bean
    LikeDomainService likeDomainService() {
        return new LikeDomainService();
    }

    @Bean
    PasswordPolicyDomainService passwordPolicyDomainService() {
        return new PasswordPolicyDomainService();
    }

    @Bean
    UserCredentialDomainService userCredentialDomainService() {
        return new UserCredentialDomainService();
    }

    @Bean
    UserModerationDomainService userModerationDomainService() {
        return new UserModerationDomainService();
    }

    @Bean
    UserReadDomainService userReadDomainService() {
        return new UserReadDomainService();
    }

    @Bean
    UserRegistrationDomainService userRegistrationDomainService() {
        return new UserRegistrationDomainService();
    }

    @Bean
    UserRoleDomainService userRoleDomainService() {
        return new UserRoleDomainService();
    }
}
