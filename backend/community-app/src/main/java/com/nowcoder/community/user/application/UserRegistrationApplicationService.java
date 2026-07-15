package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.user.application.command.CreateVerifiedRegistrationUserCommand;
import com.nowcoder.community.user.application.result.PreparedRegistrationUserResult;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.repository.UserRepository.InsertResult;
import com.nowcoder.community.user.domain.service.UserRegistrationDomainService;
import com.nowcoder.community.user.domain.service.UserRegistrationDomainService.RegistrationInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.EMAIL_ALREADY_EXISTS;
import static com.nowcoder.community.user.exception.UserErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_ALREADY_EXISTS;

@Service
public class UserRegistrationApplicationService {

    private final UserRepository userRepository;
    private final UserRegistrationDomainService userRegistrationDomainService;
    private final UuidV7Generator idGenerator;
    private final UserPolicyEventPublisher userPolicyEventPublisher;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public UserRegistrationApplicationService(
            UserRepository userRepository,
            UserRegistrationDomainService userRegistrationDomainService,
            UserPolicyEventPublisher userPolicyEventPublisher
    ) {
        this(userRepository, userRegistrationDomainService, new UuidV7Generator(), userPolicyEventPublisher);
    }

    UserRegistrationApplicationService(
            UserRepository userRepository,
            UserRegistrationDomainService userRegistrationDomainService,
            UuidV7Generator idGenerator,
            UserPolicyEventPublisher userPolicyEventPublisher
    ) {
        this.userRepository = userRepository;
        this.userRegistrationDomainService = userRegistrationDomainService;
        this.idGenerator = idGenerator;
        this.userPolicyEventPublisher = userPolicyEventPublisher;
    }

    public PreparedRegistrationUserResult prepareRegistrationUser(String username, String password, String email) {
        RegistrationInput input = userRegistrationDomainService.requireValidRegistration(username, password, email);
        if (existsByUsername(input.username())) {
            throw new BusinessException(USER_ALREADY_EXISTS);
        }
        if (existsByEmail(input.email())) {
            throw new BusinessException(EMAIL_ALREADY_EXISTS);
        }
        UserAccount prepared = userRegistrationDomainService.preparedRegistrationUser(
                idGenerator.next(),
                input,
                passwordEncoder.encode(input.password()),
                randomHeaderUrl()
        );
        return new PreparedRegistrationUserResult(
                prepared.id(),
                prepared.username(),
                prepared.email(),
                prepared.encodedPassword(),
                prepared.headerUrl()
        );
    }

    private boolean existsByUsername(String username) {
        var existing = userRepository.findByUsername(username);
        return existing != null && existing.isPresent();
    }

    private boolean existsByEmail(String email) {
        var existing = userRepository.findByEmail(email);
        return existing != null && existing.isPresent();
    }

    @Transactional
    public UserCredentialResult createVerifiedRegistrationUser(CreateVerifiedRegistrationUserCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.userId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (!hasText(command.username()) || !hasText(command.email()) || !hasText(command.encodedPassword())) {
            throw new BusinessException(INVALID_ARGUMENT, "用户名/密码/邮箱不能为空");
        }
        String encodedPassword = userRegistrationDomainService.requireValidPreparedEncodedPassword(command.encodedPassword());
        UserAccount user = userRegistrationDomainService.verifiedUser(
                command.userId(),
                command.username(),
                encodedPassword,
                command.email(),
                command.headerUrl()
        );
        InsertResult insertResult = userRepository.insertUser(user);
        if (insertResult == InsertResult.ALREADY_EXISTS) {
            return resolveExistingRegistration(user);
        }
        if (insertResult != InsertResult.CREATED) {
            throw new BusinessException(INTERNAL_ERROR, "创建用户失败");
        }
        long version = userRepository.nextUserPolicyVersion(user.id());
        userRepository.updateModerationUntil(user.id(), user.muteUntil(), user.banUntil(), version, 0L);
        publishUserPolicyChanged(user.id(), true, version);
        return toCredentialResult(user, 1);
    }

    private UserCredentialResult resolveExistingRegistration(UserAccount attempted) {
        UserAccount canonical = userRepository.findById(attempted.id()).orElse(null);
        if (canonical != null) {
            requireSameRegistrationFacts(attempted, canonical);
            return toCredentialResult(canonical, canonical.status());
        }
        if (userRepository.findByUsername(attempted.username()).isPresent()) {
            throw new BusinessException(USER_ALREADY_EXISTS);
        }
        if (userRepository.findByEmail(attempted.email()).isPresent()) {
            throw new BusinessException(EMAIL_ALREADY_EXISTS);
        }
        throw new BusinessException(INTERNAL_ERROR, "创建用户冲突但规范用户不存在");
    }

    private void requireSameRegistrationFacts(UserAccount attempted, UserAccount canonical) {
        if (!Objects.equals(attempted.id(), canonical.id())
                || !Objects.equals(attempted.username(), canonical.username())) {
            throw new BusinessException(USER_ALREADY_EXISTS, "注册回放与已有用户不一致");
        }
        if (!Objects.equals(attempted.email(), canonical.email())) {
            throw new BusinessException(EMAIL_ALREADY_EXISTS, "注册回放与已有邮箱不一致");
        }
        if (!Objects.equals(attempted.encodedPassword(), canonical.encodedPassword())
                || !Objects.equals(attempted.salt(), canonical.salt())
                || !Objects.equals(attempted.headerUrl(), canonical.headerUrl())) {
            throw new BusinessException(INTERNAL_ERROR, "注册回放与规范用户事实不一致");
        }
    }

    private UserCredentialResult toCredentialResult(UserAccount user, int status) {
        boolean allowed = status != 0;
        return new UserCredentialResult(
                user.id(),
                user.username(),
                status,
                user.type(),
                user.headerUrl(),
                user.securityVersion(),
                allowed,
                allowed
        );
    }

    private void publishUserPolicyChanged(UUID userId, boolean userExists, long version) {
        if (userId != null) {
            userPolicyEventPublisher.publishUserPolicyChanged(userId, userExists, Instant.now(), version);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String randomHeaderUrl() {
        return String.format("http://images.nowcoder.com/head/%dt.png", ThreadLocalRandom.current().nextInt(1000));
    }
}
