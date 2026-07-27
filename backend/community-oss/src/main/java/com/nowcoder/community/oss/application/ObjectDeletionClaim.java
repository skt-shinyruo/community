package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.domain.model.OssObject;

import java.util.Objects;

public record ObjectDeletionClaim(
        OssObject object,
        String storageBucket,
        String storageKey
) {

    public ObjectDeletionClaim {
        Objects.requireNonNull(object, "object must not be null");
        storageBucket = requireText(storageBucket, "storageBucket");
        storageKey = requireText(storageKey, "storageKey");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
