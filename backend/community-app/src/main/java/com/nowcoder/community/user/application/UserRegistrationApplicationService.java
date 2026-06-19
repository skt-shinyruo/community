package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.user.application.command.CreateVerifiedRegistrationUserCommand;
import com.nowcoder.community.user.application.result.PreparedRegistrationUserResult;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserRegistrationDomainService;
import com.nowcoder.community.user.domain.service.UserRegistrationDomainService.RegistrationInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.EMAIL_ALREADY_EXISTS;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_ALREADY_EXISTS;

@Service
public class UserRegistrationApplicationService {

    private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_user_username";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_user_email";
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
        if (command == null || command.userId() == null) {
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
        try {
            userRepository.insertUser(user);
        } catch (DataIntegrityViolationException ex) {
            translateDuplicateInsert(user.username(), user.email(), ex);
        }
        long version = userRepository.nextUserPolicyVersion(user.id());
        userRepository.updateModerationUntil(user.id(), user.muteUntil(), user.banUntil(), version);
        publishUserPolicyChanged(user.id(), true, version);
        return toCredentialResult(user, 1);
    }

    private void translateDuplicateInsert(String username, String email, DataIntegrityViolationException ex) {
        if (userRegistrationDomainService.causedByConstraint(ex, USERNAME_UNIQUE_CONSTRAINT)) {
            throw new BusinessException(USER_ALREADY_EXISTS, ex);
        }
        if (userRegistrationDomainService.causedByConstraint(ex, EMAIL_UNIQUE_CONSTRAINT)) {
            throw new BusinessException(EMAIL_ALREADY_EXISTS, ex);
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException(USER_ALREADY_EXISTS, ex);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(EMAIL_ALREADY_EXISTS, ex);
        }
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败", ex);
    }

    private UserCredentialResult toCredentialResult(UserAccount user, int status) {
        return new UserCredentialResult(user.id(), user.username(), status, user.type(), user.headerUrl(), user.securityVersion());
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
