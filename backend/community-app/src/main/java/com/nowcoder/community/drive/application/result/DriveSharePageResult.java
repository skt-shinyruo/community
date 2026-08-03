package com.nowcoder.community.drive.application.result;

import java.util.List;

public record DriveSharePageResult(
        List<DriveShareResult> items,
        boolean hasNext,
        int page,
        int size
) {
    public DriveSharePageResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
