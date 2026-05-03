package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.user.application.command.CreateVerifiedRegistrationUserCommand;
import com.nowcoder.community.user.application.result.PendingRegistrationUserResult;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.EMAIL_ALREADY_EXISTS;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_ALREADY_EXISTS;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserRegistrationApplicationService {

    private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_user_username";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_user_email";
    private static final int EXPIRED_PENDING_USER_BATCH_SIZE = 500;

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

    @Transactional
    public PendingRegistrationUserResult registerPendingUser(
            String username,
            String password,
            String email,
            Duration pendingTtl
    ) {
        RegistrationInput input = userRegistrationDomainService.requireValidRegistration(username, password, email);
        Instant cutoff = userRegistrationDomainService.pendingUserCutoff(pendingTtl);
        cleanupExpiredPendingConflict(userRepository.findByUsername(input.username()), cutoff);
        cleanupExpiredPendingConflict(userRepository.findByEmail(input.email()), cutoff);

        if (userRepository.findByUsername(input.username()).isPresent()) {
            throw new BusinessException(USER_ALREADY_EXISTS);
        }
        if (userRepository.findByEmail(input.email()).isPresent()) {
            throw new BusinessException(EMAIL_ALREADY_EXISTS);
        }

        UserAccount user = userRegistrationDomainService.pendingUser(
                idGenerator.next(),
                input,
                passwordEncoder.encode(input.password()),
                randomHeaderUrl()
        );
        try {
            userRepository.insertPendingUser(user);
        } catch (DataIntegrityViolationException ex) {
            translateDuplicateInsert(input.username(), input.email(), ex);
        }
        publishUserPolicyChanged(user.id(), true);
        return toPendingResult(user);
    }

    public PreparedRegistrationUserResult prepareRegistrationUser(String username, String password, String email) {
        RegistrationInput input = userRegistrationDomainService.requireValidRegistration(username, password, email);
        UserAccount prepared = userRegistrationDomainService.pendingUser(
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
        publishUserPolicyChanged(user.id(), true);
        return toCredentialResult(user, 1);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public PendingRegistrationUserResult getPendingUser(UUID userId, Duration pendingTtl) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
        Instant cutoff = userRegistrationDomainService.pendingUserCutoff(pendingTtl);
        if (userRegistrationDomainService.isExpiredPendingUser(user, cutoff)) {
            int deleted = userRepository.deletePendingUserIfExpired(user.id(), 0, cutoff);
            publishUserPolicyChangedIfDeleted(user.id(), deleted);
            throw new BusinessException(USER_NOT_FOUND, "注册已过期，请重新注册");
        }
        return toPendingResult(user);
    }

    @Transactional
    public UserCredentialResult activatePendingUser(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
        userRepository.updateStatus(userId, 1);
        publishUserPolicyChanged(userId, true);
        return toCredentialResult(user, 1);
    }

    @Transactional
    public void deletePendingUser(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        int deleted = userRepository.deletePendingUser(userId, 0);
        publishUserPolicyChangedIfDeleted(userId, deleted);
    }

    @Transactional
    public int cleanupExpiredPendingUsers(Duration pendingTtl) {
        Instant cutoff = userRegistrationDomainService.pendingUserCutoff(pendingTtl);
        List<UUID> expiredUserIds = userRepository.listExpiredPendingUserIds(0, cutoff, EXPIRED_PENDING_USER_BATCH_SIZE);
        if (expiredUserIds == null || expiredUserIds.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (UUID userId : expiredUserIds) {
            int affected = userRepository.deletePendingUserIfExpired(userId, 0, cutoff);
            deleted += affected;
            publishUserPolicyChangedIfDeleted(userId, affected);
        }
        return deleted;
    }

    private void cleanupExpiredPendingConflict(Optional<UserAccount> user, Instant cutoff) {
        if (user.isPresent() && userRegistrationDomainService.isExpiredPendingUser(user.orElseThrow(), cutoff)) {
            int deleted = userRepository.deletePendingUserIfExpired(user.orElseThrow().id(), 0, cutoff);
            publishUserPolicyChangedIfDeleted(user.orElseThrow().id(), deleted);
        }
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

    private PendingRegistrationUserResult toPendingResult(UserAccount user) {
        return new PendingRegistrationUserResult(
                user.id(),
                user.username(),
                user.email(),
                user.status(),
                user.type(),
                user.headerUrl()
        );
    }

    private UserCredentialResult toCredentialResult(UserAccount user, int status) {
        return new UserCredentialResult(user.id(), user.username(), status, user.type(), user.headerUrl());
    }

    private void publishUserPolicyChanged(UUID userId, boolean userExists) {
        if (userId != null) {
            userPolicyEventPublisher.publishUserPolicyChanged(userId, userExists, Instant.now());
        }
    }

    private void publishUserPolicyChangedIfDeleted(UUID userId, int deleted) {
        if (deleted > 0) {
            publishUserPolicyChanged(userId, false);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String randomHeaderUrl() {
        return String.format("http://images.nowcoder.com/head/%dt.png", ThreadLocalRandom.current().nextInt(1000));
    }
}
