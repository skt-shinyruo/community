package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.RegistrationRateLimitPort;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.nowcoder.community.auth.application.port.RegistrationRateLimitPort.Dimension.EMAIL;
import static com.nowcoder.community.auth.application.port.RegistrationRateLimitPort.Dimension.IP;
import static com.nowcoder.community.auth.application.port.RegistrationRateLimitPort.Dimension.REGISTRATION;
import static com.nowcoder.community.auth.application.port.RegistrationRateLimitPort.Dimension.USERNAME;
import static com.nowcoder.community.auth.application.port.RegistrationRateLimitPort.Flow.REQUEST;
import static com.nowcoder.community.auth.application.port.RegistrationRateLimitPort.Flow.RESEND;

@Component
public class RegistrationRequestRateLimiter {

    private static final String UNRESOLVED_CLIENT_IP = "<unresolved>";

    private final RegistrationProperties properties;
    private final RegistrationRateLimitPort rateLimitPort;
    private final PasswordResetTokenDeriver identifierDeriver;

    public RegistrationRequestRateLimiter(
            RegistrationProperties properties,
            RegistrationRateLimitPort rateLimitPort,
            PasswordResetTokenDeriver identifierDeriver
    ) {
        this.properties = properties;
        this.rateLimitPort = rateLimitPort;
        this.identifierDeriver = identifierDeriver;
    }

    public void enforce(String username, String email, String clientIp) {
        try {
            RegistrationProperties.RequestLimit limits = properties.getRequestLimit();
            List<RegistrationRateLimitPort.Quota> quotas = new ArrayList<>(3);
            addQuota(quotas, REQUEST, IP, clientIpOrSentinel(clientIp), limits.getMaxRequestsPerIp());
            addQuota(quotas, REQUEST, USERNAME, canonical(username), limits.getMaxRequestsPerUsername());
            addQuota(quotas, REQUEST, EMAIL, canonical(email), limits.getMaxRequestsPerEmail());
            enforceAll(REQUEST, limits.getWindowSeconds(), quotas, "注册请求过于频繁，请稍后再试");
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    public void enforceResend(UUID registrationId, String email, String clientIp) {
        try {
            if (registrationId == null || !StringUtils.hasText(email)) {
                throw new IllegalArgumentException("registration resend identity must be valid");
            }
            RegistrationProperties.ResendLimit limits = properties.getResendLimit();
            List<RegistrationRateLimitPort.Quota> quotas = new ArrayList<>(3);
            addQuota(quotas, RESEND, IP, clientIpOrSentinel(clientIp), limits.getMaxRequestsPerIp());
            addQuota(quotas, RESEND, EMAIL, canonical(email), limits.getMaxRequestsPerEmail());
            addQuota(quotas, RESEND, REGISTRATION,
                    registrationId.toString(), limits.getMaxRequestsPerRegistration());
            enforceAll(RESEND, limits.getWindowSeconds(), quotas, "注册验证码发送过于频繁，请稍后再试");
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private void addQuota(
            List<RegistrationRateLimitPort.Quota> quotas,
            RegistrationRateLimitPort.Flow flow,
            RegistrationRateLimitPort.Dimension dimension,
            String value,
            int maximum
    ) {
        if (!StringUtils.hasText(value) || maximum <= 0) {
            return;
        }
        String scope = "registration-" + flow.name().toLowerCase(Locale.ROOT)
                + "-" + dimension.name().toLowerCase(Locale.ROOT);
        String opaqueId = identifierDeriver.identifierId(scope, value);
        quotas.add(new RegistrationRateLimitPort.Quota(dimension, opaqueId, maximum));
    }

    private void enforceAll(
            RegistrationRateLimitPort.Flow flow,
            int windowSeconds,
            List<RegistrationRateLimitPort.Quota> quotas,
            String throttledMessage
    ) {
        if (quotas.isEmpty()) {
            return;
        }
        boolean allowed = rateLimitPort.tryConsume(
                flow,
                Duration.ofSeconds(Math.max(1, windowSeconds)),
                List.copyOf(quotas)
        );
        if (!allowed) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, throttledMessage);
        }
    }

    private BusinessException unavailable(RuntimeException exception) {
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                "注册风控暂时不可用，请稍后重试", exception);
    }

    private String clientIpOrSentinel(String clientIp) {
        String canonicalIp = canonical(clientIp);
        return StringUtils.hasText(canonicalIp) ? canonicalIp : UNRESOLVED_CLIENT_IP;
    }

    private String canonical(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String folded = value.trim().toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(folded, Normalizer.Form.NFKD);
        StringBuilder result = new StringBuilder(decomposed.length());
        decomposed.codePoints()
                .filter(codePoint -> {
                    int type = Character.getType(codePoint);
                    return type != Character.NON_SPACING_MARK
                            && type != Character.COMBINING_SPACING_MARK
                            && type != Character.ENCLOSING_MARK;
                })
                .forEach(result::appendCodePoint);
        return result.toString();
    }
}
