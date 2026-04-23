package com.nowcoder.community.im.projection;

import com.nowcoder.community.common.web.SkipResultWrap;
import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@SkipResultWrap
@RequestMapping("/internal/im/realtime/projections")
public class ImPolicySnapshotController {

    private final ImPolicySnapshotService snapshotService;

    public ImPolicySnapshotController(ImPolicySnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/user-policies")
    public UserMessagingPolicySnapshot userPolicies(
            @RequestParam(name = "afterUserId", required = false) UUID afterUserId,
            @RequestParam(name = "limit", defaultValue = "500") int limit
    ) {
        try {
            return snapshotService.userPolicies(afterUserId, limit);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/block-relations")
    public UserBlockRelationSnapshot blockRelations(
            @RequestParam(name = "afterBlockerUserId", required = false) UUID afterBlockerUserId,
            @RequestParam(name = "afterBlockedUserId", required = false) UUID afterBlockedUserId,
            @RequestParam(name = "limit", defaultValue = "500") int limit
    ) {
        try {
            return snapshotService.blockRelations(afterBlockerUserId, afterBlockedUserId, limit);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
