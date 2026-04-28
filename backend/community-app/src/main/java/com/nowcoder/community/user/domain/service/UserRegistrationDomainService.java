package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.domain.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class UserRegistrationDomainService {

    private final Clock clock;

    public UserRegistrationDomainService() {
        this(Clock.systemUTC());
    }

    public UserRegistrationDomainService(Clock clock) {
        this.clock = clock;
    }

    public RegistrationInput requireValidRegistration(String username, String password, String email) {
        String trimmedUsername = safeTrim(username);
        String trimmedPassword = safeTrim(password);
        String trimmedEmail = safeTrim(email);
        if (!StringUtils.hasText(trimmedUsername)
                || !StringUtils.hasText(trimmedPassword)
                || !StringUtils.hasText(trimmedEmail)) {
            throw new BusinessException(INVALID_ARGUMENT, "用户名/密码/邮箱不能为空");
        }
        return new RegistrationInput(trimmedUsername, trimmedPassword, trimmedEmail);
    }

    public Instant pendingUserCutoff(Duration pendingTtl) {
        Duration ttl = pendingTtl == null || pendingTtl.isZero() || pendingTtl.isNegative()
                ? Duration.ofMinutes(30)
                : pendingTtl;
        return Instant.now(clock).minus(ttl);
    }

    public boolean isExpiredPendingUser(UserAccount user, Instant cutoff) {
        if (user == null || cutoff == null || user.createTime() == null) {
            return false;
        }
        if (user.status() != 0) {
            return false;
        }
        return !user.createTime().toInstant().isAfter(cutoff);
    }

    public boolean causedByConstraint(Throwable error, String constraintName) {
        String expected = constraintName.toLowerCase(Locale.ROOT);
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(expected)) {
                return true;
            }
        }
        return false;
    }

    public UserAccount pendingUser(
            java.util.UUID userId,
            RegistrationInput input,
            String encodedPassword,
            String headerUrl
    ) {
        return new UserAccount(
                userId,
                input.username(),
                encodedPassword,
                "",
                input.email(),
                0,
                0,
                headerUrl,
                Date.from(Instant.now(clock)),
                0,
                null,
                null
        );
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public record RegistrationInput(String username, String password, String email) {
    }
}
