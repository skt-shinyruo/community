package com.nowcoder.community.auth.application.port;

import java.time.Duration;
import java.util.List;

public interface RegistrationRateLimitPort {

    boolean tryConsume(Flow flow, Duration window, List<Quota> quotas);

    enum Flow {
        REQUEST,
        RESEND
    }

    enum Dimension {
        IP,
        USERNAME,
        EMAIL,
        REGISTRATION
    }

    record Quota(Dimension dimension, String opaqueIdentifier, int maximum) {
    }
}
