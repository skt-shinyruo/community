package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.port.UsernameAuthenticationSubjectPort;
import com.nowcoder.community.user.application.result.UserAuthenticationResult;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.PasswordPolicyDomainService;
import com.nowcoder.community.user.domain.service.UserCredentialDomainService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.SERVICE_UNAVAILABLE;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserCredentialApplicationService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$pEbLAXD.5j9U47tFYwlcM.xJyDlxVuxJ/RCBkcWAQHSwBS9w/vKKm";

    private final UserRepository userRepository;
    private final UserCredentialDomainService userCredentialDomainService;
    private final PasswordPolicyDomainService passwordPolicyDomainService;
    private final UsernameAuthenticationSubjectPort usernameAuthenticationSubjectPort;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserCredentialApplicationService(
            UserRepository userRepository,
            UserCredentialDomainService userCredentialDomainService,
            PasswordPolicyDomainService passwordPolicyDomainService,
            UsernameAuthenticationSubjectPort usernameAuthenticationSubjectPort
    ) {
        this.userRepository = userRepository;
        this.userCredentialDomainService = userCredentialDomainService;
        this.passwordPolicyDomainService = passwordPolicyDomainService;
        this.usernameAuthenticationSubjectPort = usernameAuthenticationSubjectPort;
    }

    public UserAuthenticationResult authenticate(String username, String password) {
        if (!StringUtils.hasText(userCredentialDomainService.trim(username))
                || !StringUtils.hasText(password)) {
            return UserAuthenticationResult.invalidCredentials();
        }
        return authenticate(prepareAuthentication(username), password);
    }

    public PreparedAuthentication prepareAuthentication(String username) {
        String trimmedUsername = userCredentialDomainService.trim(username);
        if (!StringUtils.hasText(trimmedUsername) || !userCredentialDomainService.isSafeUsername(trimmedUsername)) {
            return new PreparedAuthentication(null, DUMMY_PASSWORD_HASH, false);
        }

        UserAccount user = userRepository.findByUsername(trimmedUsername).orElse(null);
        if (user != null && !userCredentialDomainService.isSafeUsername(user.username())) {
            return new PreparedAuthentication(null, DUMMY_PASSWORD_HASH, false);
        }
        boolean storedHashUsable = user != null && userCredentialDomainService.isBcrypt(user.encodedPassword());
        String encodedPassword = storedHashUsable ? user.encodedPassword() : DUMMY_PASSWORD_HASH;
        return new PreparedAuthentication(user, encodedPassword, storedHashUsable);
    }

    public String authenticationSubject(String username) {
        String trimmedUsername = userCredentialDomainService.trim(username);
        if (!StringUtils.hasText(trimmedUsername) || !userCredentialDomainService.isSafeUsername(trimmedUsername)) {
            throw new BusinessException(INVALID_ARGUMENT, "username 非法");
        }
        String subject;
        try {
            subject = usernameAuthenticationSubjectPort.resolve(trimmedUsername);
        } catch (RuntimeException exception) {
            throw new BusinessException(SERVICE_UNAVAILABLE, "用户认证服务暂时不可用", exception);
        }
        if (!StringUtils.hasText(subject)) {
            throw new BusinessException(SERVICE_UNAVAILABLE, "用户认证服务暂时不可用");
        }
        return subject.trim();
    }

    public UserAuthenticationResult authenticate(PreparedAuthentication preparation, String password) {
        PreparedAuthentication safePreparation = preparation == null
                ? new PreparedAuthentication(null, DUMMY_PASSWORD_HASH, false)
                : preparation;
        String rawPassword = password == null ? "" : password;
        if (!StringUtils.hasText(rawPassword)) {
            return UserAuthenticationResult.invalidCredentials();
        }

        UserAccount user = safePreparation.user();
        String encodedPassword = StringUtils.hasText(safePreparation.encodedPassword())
                ? safePreparation.encodedPassword()
                : DUMMY_PASSWORD_HASH;
        boolean passwordMatches = passwordMatches(rawPassword, encodedPassword);
        // A dummy hash keeps timing uniform, but must never authenticate a real
        // account whose stored hash is missing or malformed.
        if (user == null || !safePreparation.storedHashUsable() || !passwordMatches) {
            return UserAuthenticationResult.invalidCredentials();
        }
        if (user.status() == 0 || activeBan(user)) {
            return UserAuthenticationResult.userDisabled(toCredentialResult(user));
        }
        return UserAuthenticationResult.authenticated(toCredentialResult(user));
    }

    public record PreparedAuthentication(UserAccount user, String encodedPassword, boolean storedHashUsable) {

        public PreparedAuthentication(UserAccount user, String encodedPassword) {
            this(user, encodedPassword, user != null && encodedPassword != null
                    && encodedPassword.matches("\\A\\$2[aby]\\$(?:0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}\\z"));
        }
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
        String value = userCredentialDomainService.canonicalEmail(email);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "email 不能为空");
        }
        return userRepository.findByEmail(value).map(this::toCredentialResult).orElse(null);
    }

    @Transactional
    public void updatePassword(UUID userId, String newPassword) {
        updatePasswordOnly(userId, newPassword);
    }

    @Transactional
    public boolean updatePasswordIfSecurityVersion(
            UUID userId,
            String newPassword,
            long expectedSecurityVersion
    ) {
        if (userId == null || expectedSecurityVersion < 0L) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/securityVersion 非法");
        }
        String validatedPassword = passwordPolicyDomainService.requireValidPassword(newPassword);
        String encodedPassword = passwordEncoder.encode(validatedPassword);
        long securityVersion = userRepository.nextUserSecurityVersion(userId);
        return userRepository.updatePasswordIfSecurityVersion(
                userId,
                encodedPassword,
                securityVersion,
                expectedSecurityVersion
        );
    }

    public void validatePasswordPolicy(String newPassword) {
        passwordPolicyDomainService.requireValidPassword(newPassword);
    }

    private void updatePasswordOnly(UUID userId, String newPassword) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        String validatedPassword = passwordPolicyDomainService.requireValidPassword(newPassword);
        if (userRepository.findById(userId).isEmpty()) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        long securityVersion = userRepository.nextUserSecurityVersion(userId);
        userRepository.updatePassword(userId, passwordEncoder.encode(validatedPassword), securityVersion);
    }

    public List<String> authoritiesOf(UserCredentialResult user) {
        return user == null ? List.of() : userCredentialDomainService.authoritiesForType(user.type());
    }

    private boolean passwordMatches(String rawPassword, String encodedPassword) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(encodedPassword)) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean activeBan(UserAccount user) {
        return user != null && user.banUntil() != null && user.banUntil().isAfter(Instant.now());
    }

    private UserCredentialResult toCredentialResult(UserAccount user) {
        boolean allowed = user.status() != 0 && !activeBan(user);
        return new UserCredentialResult(
                user.id(),
                user.username(),
                user.email(),
                user.status(),
                user.type(),
                user.headerUrl(),
                user.securityVersion(),
                allowed,
                allowed
        );
    }
}
