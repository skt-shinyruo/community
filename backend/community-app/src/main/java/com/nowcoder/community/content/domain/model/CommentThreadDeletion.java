package com.nowcoder.community.content.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CommentThreadDeletion(
        UUID rootCommentId,
        List<Target> targets,
        UUID deletedBy,
        String deletedReason,
        Date deletedTime
) {
    public CommentThreadDeletion {
        Objects.requireNonNull(rootCommentId, "rootCommentId must not be null");
        Objects.requireNonNull(targets, "targets must not be null");
        Objects.requireNonNull(deletedBy, "deletedBy must not be null");
        Objects.requireNonNull(deletedTime, "deletedTime must not be null");
        targets = List.copyOf(targets);
        if (targets.isEmpty() || !rootCommentId.equals(targets.get(0).commentId())) {
            throw new IllegalArgumentException("root target must be first");
        }
        Set<UUID> commentIds = new HashSet<>();
        for (Target target : targets) {
            if (!commentIds.add(target.commentId())) {
                throw new IllegalArgumentException("duplicate target commentId: " + target.commentId());
            }
        }
        deletedTime = new Date(deletedTime.getTime());
    }

    public static CommentThreadDeletion from(
            CommentDeletion rootDeletion,
            List<CommentSnapshot> activeThreadSnapshots
    ) {
        Objects.requireNonNull(rootDeletion, "rootDeletion must not be null");
        Objects.requireNonNull(activeThreadSnapshots, "activeThreadSnapshots must not be null");
        UUID rootCommentId = rootDeletion.commentId();
        List<CommentSnapshot> ordered = new ArrayList<>(activeThreadSnapshots);
        ordered.removeIf(snapshot -> snapshot == null || !snapshot.active());
        ordered.sort(Comparator
                .comparing((CommentSnapshot snapshot) -> !rootCommentId.equals(snapshot.id()))
                .thenComparing(CommentSnapshot::createTime)
                .thenComparing(snapshot -> snapshot.id().toString()));
        if (ordered.isEmpty() || !rootCommentId.equals(ordered.get(0).id())) {
            throw new IllegalArgumentException("active root snapshot must be first");
        }
        List<Target> targets = ordered.stream()
                .map(snapshot -> {
                    if (!rootCommentId.equals(snapshot.rootCommentId())) {
                        throw new IllegalArgumentException("snapshot does not belong to root thread");
                    }
                    long expectedVersion = rootCommentId.equals(snapshot.id())
                            ? rootDeletion.expectedVersion()
                            : snapshot.version();
                    return new Target(snapshot.id(), expectedVersion);
                })
                .toList();
        return new CommentThreadDeletion(
                rootCommentId,
                targets,
                rootDeletion.deletedBy(),
                rootDeletion.deletedReason(),
                rootDeletion.deletedTime()
        );
    }

    @Override
    public Date deletedTime() {
        return new Date(deletedTime.getTime());
    }

    public record Target(UUID commentId, long expectedVersion) {
        public Target {
            Objects.requireNonNull(commentId, "commentId must not be null");
            if (expectedVersion < 0) {
                throw new IllegalArgumentException("expectedVersion must not be negative");
            }
        }
    }
}
