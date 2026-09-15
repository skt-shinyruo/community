package com.nowcoder.community.im.realtime.session;

import java.util.UUID;

/**
 * Read-only identity of one realtime connection: the stable connection id and the
 * authenticated user it is bound to. Presence, push and coalescing modules key their
 * bookkeeping off this identity without seeing transport or mutable internals.
 */
public interface ConnectionIdentity {

    String connectionId();

    UUID userId();
}
