package com.nowcoder.community.user.application;

import com.nowcoder.community.user.domain.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class UserAvatarTransactionOperations {

    private final UserRepository userRepository;

    public UserAvatarTransactionOperations(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void updateHeaderUrl(UUID userId, String headerUrl) {
        userRepository.updateHeaderUrl(userId, headerUrl);
    }
}
