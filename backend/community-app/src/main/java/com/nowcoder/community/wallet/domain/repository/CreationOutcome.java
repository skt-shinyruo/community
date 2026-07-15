package com.nowcoder.community.wallet.domain.repository;

import java.util.Objects;

public record CreationOutcome<T>(Status status, T aggregate) {

    public CreationOutcome {
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.CONFLICT) {
            if (aggregate != null) {
                throw new IllegalArgumentException("conflict outcome must not carry an aggregate");
            }
        } else {
            Objects.requireNonNull(aggregate, "successful creation outcome must carry an aggregate");
        }
    }

    public enum Status {
        CREATED,
        ALREADY_EXISTS,
        CONFLICT
    }

    public static <T> CreationOutcome<T> created(T aggregate) {
        return new CreationOutcome<>(Status.CREATED, aggregate);
    }

    public static <T> CreationOutcome<T> alreadyExists(T aggregate) {
        return new CreationOutcome<>(Status.ALREADY_EXISTS, aggregate);
    }

    public static <T> CreationOutcome<T> conflict() {
        return new CreationOutcome<>(Status.CONFLICT, null);
    }
}
