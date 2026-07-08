package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.domain.model.UserAccount;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class UserRegistrationDomainService {

    private static final Pattern BCRYPT_PASSWORD_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final Clock clock;
    private final PasswordPolicyDomainService passwordPolicyDomainService;

    public UserRegistrationDomainService() {
        this(Clock.systemUTC(), new PasswordPolicyDomainService());
    }

    public UserRegistrationDomainService(Clock clock) {
        this(clock, new PasswordPolicyDomainService());
    }

    public UserRegistrationDomainService(Clock clock, PasswordPolicyDomainService passwordPolicyDomainService) {
        this.clock = clock;
        this.passwordPolicyDomainService = passwordPolicyDomainService;
    }

    public RegistrationInput requireValidRegistration(String username, String password, String email) {
        String trimmedUsername = safeTrim(username);
        String validatedPassword = passwordPolicyDomainService.requireValidPassword(password);
        String trimmedEmail = safeTrim(email);
        if (!hasText(trimmedUsername)
                || !hasText(validatedPassword)
                || !hasText(trimmedEmail)) {
            throw new BusinessException(INVALID_ARGUMENT, "用户名/密码/邮箱不能为空");
        }
        return new RegistrationInput(trimmedUsername, validatedPassword, trimmedEmail);
    }

    public String requireValidPreparedEncodedPassword(String encodedPassword) {
        String trimmedEncodedPassword = safeTrim(encodedPassword);
        if (!BCRYPT_PASSWORD_PATTERN.matcher(trimmedEncodedPassword).matches()) {
            throw new BusinessException(INVALID_ARGUMENT, "密码格式非法");
        }
        return trimmedEncodedPassword;
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

    public UserAccount preparedRegistrationUser(
            java.util.UUID userId,
            RegistrationInput input,
            String encodedPassword,
            String headerUrl
    ) {
        return new UserAccount(userId, input.username(), encodedPassword, "", input.email(), 0, 0, headerUrl, Date.from(Instant.now(clock)), null, null, 0L, 0L);
    }

    public UserAccount verifiedUser(
            java.util.UUID userId,
            String username,
            String encodedPassword,
            String email,
            String headerUrl
    ) {
        return new UserAccount(userId, safeTrim(username), safeTrim(encodedPassword), "", safeTrim(email), 0, 1, safeTrim(headerUrl), Date.from(Instant.now(clock)), null, null, 0L, 0L);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record RegistrationInput(String username, String password, String email) {
    }
}
