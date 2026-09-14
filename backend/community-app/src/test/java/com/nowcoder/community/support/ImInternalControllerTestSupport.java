package com.nowcoder.community.support;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.infrastructure.persistence.dataobject.UserDataObject;
import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Shared fixtures for community-app internal HTTP controller tests: service tokens scoped for the
 * IM internal chain and user rows carrying a valid policy version log entry.
 */
public final class ImInternalControllerTestSupport {

    public static final String IM_INTERNAL_SCOPE = "im.realtime.internal";

    private ImInternalControllerTestSupport() {
    }

    public static String bearer(JwtProperties jwtProperties, UUID subject) {
        return serviceBearer(jwtProperties, subject, null);
    }

    public static String internalBearer(JwtProperties jwtProperties, UUID subject) {
        return serviceBearer(jwtProperties, subject, IM_INTERNAL_SCOPE);
    }

    public static String serviceBearer(JwtProperties jwtProperties, UUID subject, String scope) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(JwtCodecs.resolvedIssuer(jwtProperties))
                .audience(List.of("community-app"))
                .subject(String.valueOf(subject))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(120));
        if (scope != null && !scope.isBlank()) {
            claimsBuilder.claim("scope", scope);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type(JwtCodecs.SERVICE_TOKEN_TYPE)
                .build();
        String token = JwtCodecs.serviceTokenEncoder(jwtProperties)
                .encode(JwtEncoderParameters.from(header, claimsBuilder.build()))
                .getTokenValue();
        return "Bearer " + token;
    }

    public static void insertUser(UserMapper userMapper, UserRepository userRepository, UUID userId, String username) {
        insertUser(userMapper, userRepository, userId, username, 0, 0);
    }

    public static void insertUser(
            UserMapper userMapper,
            UserRepository userRepository,
            UUID userId,
            String username,
            int type,
            int status
    ) {
        UserDataObject user = new UserDataObject();
        user.setId(userId);
        user.setUsername(username);
        user.setPassword("encoded");
        user.setSalt("");
        user.setEmail(username + "@example.com");
        user.setType(type);
        user.setStatus(status);
        user.setHeaderUrl("/avatar.png");
        user.setCreateTime(new Date());
        user.setPolicyVersion(userRepository.nextUserPolicyVersion(userId));
        userMapper.insertUser(user);
        userMapper.insertPolicyVersionLog(user.getPolicyVersion(), userId, true, null, null);
    }
}
