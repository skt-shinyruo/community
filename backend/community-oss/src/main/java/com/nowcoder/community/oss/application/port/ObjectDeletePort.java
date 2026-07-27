package com.nowcoder.community.oss.application.port;

/**
 * Deletes object content from the external object store.
 *
 * <p>Implementations must treat an already absent object as a successful deletion so callers can
 * safely retry after an indeterminate failure.</p>
 */
public interface ObjectDeletePort {

    void deleteIfExists(String bucket, String key);
}
