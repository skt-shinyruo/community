package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserRole;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class UserRegistrationDomainService {

    private static final Pattern BCRYPT_PASSWORD_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    private static final int PREPARED_ACCOUNT_STATUS = 0;
    private static final int ACTIVE_ACCOUNT_STATUS = 1;

    private final Clock clock;
    private final PasswordPolicyDomainService passwordPolicyDomainService;
    private final UsernamePolicyDomainService usernamePolicyDomainService;

    public UserRegistrationDomainService(
            Clock clock,
            PasswordPolicyDomainService passwordPolicyDomainService,
            UsernamePolicyDomainService usernamePolicyDomainService
    ) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.passwordPolicyDomainService = Objects.requireNonNull(
                passwordPolicyDomainService,
                "passwordPolicyDomainService must not be null"
        );
        this.usernamePolicyDomainService = Objects.requireNonNull(
                usernamePolicyDomainService,
                "usernamePolicyDomainService must not be null"
        );
    }

    public RegistrationInput requireValidRegistration(String username, String password, String email) {
        String trimmedUsername = usernamePolicyDomainService.requireValid(username);
        String validatedPassword = passwordPolicyDomainService.requireValidPassword(password);
        String trimmedEmail = canonicalEmail(email);
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

    public UserAccount preparedRegistrationUser(
            java.util.UUID userId,
            RegistrationInput input,
            String encodedPassword,
            String headerUrl
    ) {
        return registrationUser(
                userId,
                input.username(),
                encodedPassword,
                input.email(),
                PREPARED_ACCOUNT_STATUS,
                headerUrl
        );
    }

    public UserAccount verifiedUser(
            java.util.UUID userId,
            String username,
            String encodedPassword,
            String email,
            String headerUrl
    ) {
        return registrationUser(
                userId,
                usernamePolicyDomainService.requireValid(username),
                safeTrim(encodedPassword),
                canonicalEmail(email),
                ACTIVE_ACCOUNT_STATUS,
                safeTrim(headerUrl)
        );
    }

    private UserAccount registrationUser(
            java.util.UUID userId,
            String username,
            String encodedPassword,
            String email,
            int status,
            String headerUrl
    ) {
        String legacySalt = "";
        int initialRole = UserRole.USER.type();
        Date createdAt = Date.from(Instant.now(clock));
        Instant muteUntil = null;
        Instant banUntil = null;
        long initialPolicyVersion = 0L;
        long initialSecurityVersion = 0L;
        return new UserAccount(
                userId,
                username,
                encodedPassword,
                legacySalt,
                email,
                initialRole,
                status,
                headerUrl,
                createdAt,
                muteUntil,
                banUntil,
                initialPolicyVersion,
                initialSecurityVersion
        );
    }

    public boolean credentialIssuanceAllowed(UserAccount user) {
        if (user == null || user.status() == 0) {
            return false;
        }
        return user.banUntil() == null || !user.banUntil().isAfter(Instant.now(clock));
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String canonicalEmail(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record RegistrationInput(String username, String password, String email) {
    }
}
