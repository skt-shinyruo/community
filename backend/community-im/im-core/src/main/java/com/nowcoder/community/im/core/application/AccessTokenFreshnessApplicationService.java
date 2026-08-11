package com.nowcoder.community.im.core.application;

import org.springframework.stereotype.Service;

@Service
public class AccessTokenFreshnessApplicationService {

    private final OwnerVerifier ownerVerifier;

    public AccessTokenFreshnessApplicationService(OwnerVerifier ownerVerifier) {
        this.ownerVerifier = ownerVerifier;
    }

    public Decision verify(String accessToken) {
        try {
            Decision decision = ownerVerifier.verify(accessToken);
            return decision == null ? Decision.UNAVAILABLE : decision;
        } catch (RuntimeException exception) {
            return Decision.UNAVAILABLE;
        }
    }

    public interface OwnerVerifier {

        Decision verify(String accessToken);
    }

    public enum Decision {
        FRESH,
        STALE,
        DENIED,
        UNAVAILABLE
    }
}
