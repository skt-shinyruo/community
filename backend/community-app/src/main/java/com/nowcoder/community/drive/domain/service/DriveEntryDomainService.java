package com.nowcoder.community.drive.domain.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DriveEntryDomainService {

    public String normalizeName(String name) {
        String value = Objects.toString(name, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("entry name must not be blank");
        }
        if (value.contains("/") || value.contains("\\") || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("entry name is invalid");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("entry name is too long");
        }
        return value;
    }

    public void assertCanMove(UUID entryId, UUID newParentId, List<UUID> descendantIds) {
        if (entryId == null || newParentId == null) {
            return;
        }
        if (entryId.equals(newParentId) || (descendantIds != null && descendantIds.contains(newParentId))) {
            throw new IllegalArgumentException("folder cannot be moved into itself or descendant");
        }
    }
}
