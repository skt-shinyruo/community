package com.nowcoder.community.gateway.ws;

public record ExternalWsSessionState(Phase phase, String userId) {

    public static ExternalWsSessionState connectedUnauthed() {
        return new ExternalWsSessionState(Phase.CONNECTED_UNAUTHED, "");
    }

    public static ExternalWsSessionState authValidating() {
        return new ExternalWsSessionState(Phase.AUTH_VALIDATING, "");
    }

    public static ExternalWsSessionState authedReady(String userId) {
        return new ExternalWsSessionState(Phase.AUTHED_READY, userId == null ? "" : userId);
    }

    public static ExternalWsSessionState closing(String userId) {
        return new ExternalWsSessionState(Phase.CLOSING, userId == null ? "" : userId);
    }

    public enum Phase {
        CONNECTED_UNAUTHED,
        AUTH_VALIDATING,
        AUTHED_READY,
        CLOSING
    }
}
