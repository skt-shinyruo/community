package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.action.UserCredentialActionApi;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi.AuthenticationChallenge;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi.AuthenticationSubject;
import com.nowcoder.community.user.application.port.UsernameAuthenticationSubjectPort;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.PasswordPolicyDomainService;
import com.nowcoder.community.user.domain.service.UserCredentialDomainService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.SERVICE_UNAVAILABLE;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserCredentialApplicationService implements UserCredentialQueryApi, UserCredentialActionApi {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$pEbLAXD.5j9U47tFYwlcM.xJyDlxVuxJ/RCBkcWAQHSwBS9w/vKKm";

    private final UserRepository userRepository;
    private final UserCredentialDomainService userCredentialDomainService;
    private final PasswordPolicyDomainService passwordPolicyDomainService;
    private final UsernameAuthenticationSubjectPort usernameAuthenticationSubjectPort;
    private final Clock clock;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserCredentialApplicationService(
            UserRepository userRepository,
            UserCredentialDomainService userCredentialDomainService,
            PasswordPolicyDomainService passwordPolicyDomainService,
            UsernameAuthenticationSubjectPort usernameAuthenticationSubjectPort,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.userCredentialDomainService = Objects.requireNonNull(
                userCredentialDomainService,
                "userCredentialDomainService must not be null"
        );
        this.passwordPolicyDomainService = Objects.requireNonNull(
                passwordPolicyDomainService,
                "passwordPolicyDomainService must not be null"
        );
        this.usernameAuthenticationSubjectPort = Objects.requireNonNull(
                usernameAuthenticationSubjectPort,
                "usernameAuthenticationSubjectPort must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public UserAuthenticationResultView authenticate(String username, String password) {
        if (!StringUtils.hasText(userCredentialDomainService.trim(username))
                || !StringUtils.hasText(password)) {
            return UserAuthenticationResultView.invalidCredentials();
        }
        return authenticate(prepare(username), password);
    }

    public PreparedAuthentication prepare(String username) {
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

    @Override
    public AuthenticationChallenge prepareAuthentication(String username) {
        return new OneShotAuthenticationChallenge(prepare(username));
    }

    @Override
    public AuthenticationSubject authenticationSubject(String username) {
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
        return new AuthenticationSubject(subject.trim());
    }

    public UserAuthenticationResultView authenticate(PreparedAuthentication preparation, String password) {
        PreparedAuthentication safePreparation = preparation == null
                ? new PreparedAuthentication(null, DUMMY_PASSWORD_HASH, false)
                : preparation;
        String rawPassword = password == null ? "" : password;
        if (!StringUtils.hasText(rawPassword)) {
            return UserAuthenticationResultView.invalidCredentials();
        }

        UserAccount user = safePreparation.user();
        String encodedPassword = StringUtils.hasText(safePreparation.encodedPassword())
                ? safePreparation.encodedPassword()
                : DUMMY_PASSWORD_HASH;
        boolean passwordMatches = passwordMatches(rawPassword, encodedPassword);
        // A dummy hash keeps timing uniform, but must never authenticate a real
        // account whose stored hash is missing or malformed.
        if (user == null || !safePreparation.storedHashUsable() || !passwordMatches) {
            return UserAuthenticationResultView.invalidCredentials();
        }
        if (user.status() == 0 || activeBan(user)) {
            return UserAuthenticationResultView.userDisabled(toCredentialView(user));
        }
        return UserAuthenticationResultView.authenticated(toCredentialView(user));
    }

    public record PreparedAuthentication(UserAccount user, String encodedPassword, boolean storedHashUsable) {

    }

    @Override
    public UserCredentialView getByUserId(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return userRepository.findById(userId).map(this::toCredentialView).orElse(null);
    }

    @Override
    public UserCredentialView findByEmailOrNull(String email) {
        String value = userCredentialDomainService.canonicalEmail(email);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "email 不能为空");
        }
        return userRepository.findByEmail(value).map(this::toCredentialView).orElse(null);
    }

    @Transactional
    public void updatePassword(UUID userId, String newPassword) {
        updatePasswordOnly(userId, newPassword);
    }

    @Transactional
    @Override
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

    @Override
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

    @Override
    public List<String> authoritiesOf(UserCredentialView user) {
        return user == null ? List.of() : userCredentialDomainService.authoritiesForType(user.type());
    }

    private final class OneShotAuthenticationChallenge implements AuthenticationChallenge {

        private final UUID userId;
        private final AtomicReference<PreparedAuthentication> preparation;

        private OneShotAuthenticationChallenge(PreparedAuthentication preparation) {
            this.userId = preparation == null || preparation.user() == null
                    ? null
                    : preparation.user().id();
            this.preparation = new AtomicReference<>(preparation);
        }

        @Override
        public UUID userId() {
            return userId;
        }

        @Override
        public UserAuthenticationResultView authenticate(String password) {
            PreparedAuthentication current = preparation.getAndSet(null);
            if (current == null) {
                return UserAuthenticationResultView.invalidCredentials();
            }
            UserAuthenticationResultView result = UserCredentialApplicationService.this.authenticate(current, password);
            return result == null ? UserAuthenticationResultView.invalidCredentials() : result;
        }
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
        return user != null && user.banUntil() != null && user.banUntil().isAfter(Instant.now(clock));
    }

    private UserCredentialView toCredentialView(UserAccount user) {
        boolean allowed = user.status() != 0 && !activeBan(user);
        return new UserCredentialView(
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
