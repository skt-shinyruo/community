package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.result.UserAuthenticationResult;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.RefreshTokenSessionRepository;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.PasswordPolicyDomainService;
import com.nowcoder.community.user.domain.service.UserCredentialDomainService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserCredentialApplicationService {

    private final UserRepository userRepository;
    private final UserCredentialDomainService userCredentialDomainService;
    private final PasswordPolicyDomainService passwordPolicyDomainService;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserCredentialApplicationService(
            UserRepository userRepository,
            UserCredentialDomainService userCredentialDomainService,
            PasswordPolicyDomainService passwordPolicyDomainService,
            RefreshTokenSessionRepository refreshTokenSessionRepository
    ) {
        this.userRepository = userRepository;
        this.userCredentialDomainService = userCredentialDomainService;
        this.passwordPolicyDomainService = passwordPolicyDomainService;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
    }

    public UserAuthenticationResult authenticate(String username, String password) {
        String trimmedUsername = userCredentialDomainService.trim(username);
        String rawPassword = password == null ? "" : password;
        if (!StringUtils.hasText(trimmedUsername) || !StringUtils.hasText(rawPassword)) {
            return UserAuthenticationResult.invalidCredentials();
        }

        UserAccount user = userRepository.findByUsername(trimmedUsername).orElse(null);
        if (user == null) {
            return UserAuthenticationResult.invalidCredentials();
        }
        if (user.status() == 0) {
            return UserAuthenticationResult.userDisabled(toCredentialResult(user));
        }
        if (!passwordMatches(user, rawPassword)) {
            return UserAuthenticationResult.invalidCredentials();
        }
        return UserAuthenticationResult.authenticated(toCredentialResult(user));
    }

    public UserCredentialResult getByUserId(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return userRepository.findById(userId)
                .map(this::toCredentialResult)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    public UserCredentialResult findByEmailOrNull(String email) {
        String value = userCredentialDomainService.trim(email);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "email 不能为空");
        }
        return userRepository.findByEmail(value).map(this::toCredentialResult).orElse(null);
    }

    public void updatePassword(UUID userId, String newPassword) {
        updatePasswordOnly(userId, newPassword);
    }

    public void validatePasswordPolicy(String newPassword) {
        passwordPolicyDomainService.requireValidPassword(newPassword);
    }

    @Transactional
    public void resetPasswordAndRevokeRefreshSessions(UUID userId, String newPassword) {
        updatePasswordOnly(userId, newPassword);
        refreshTokenSessionRepository.revokeByUserId(userId);
    }

    private void updatePasswordOnly(UUID userId, String newPassword) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        String validatedPassword = passwordPolicyDomainService.requireValidPassword(newPassword);
        if (userRepository.findById(userId).isEmpty()) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        userRepository.updatePassword(userId, passwordEncoder.encode(validatedPassword));
    }

    public List<String> authoritiesOf(UserCredentialResult user) {
        return user == null ? List.of() : userCredentialDomainService.authoritiesForType(user.type());
    }

    private boolean passwordMatches(UserAccount user, String rawPassword) {
        if (user == null || !StringUtils.hasText(rawPassword) || !StringUtils.hasText(user.encodedPassword())) {
            return false;
        }
        if (userCredentialDomainService.isBcrypt(user.encodedPassword())) {
            try {
                return passwordEncoder.matches(rawPassword, user.encodedPassword());
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    private UserCredentialResult toCredentialResult(UserAccount user) {
        return new UserCredentialResult(
                user.id(),
                user.username(),
                user.status(),
                user.type(),
                user.headerUrl(),
                user.securityVersion()
        );
    }
}
