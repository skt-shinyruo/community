package com.nowcoder.community.drive.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.drive.application.DriveShareApplicationService;
import com.nowcoder.community.drive.application.command.VerifyDriveShareCommand;
import com.nowcoder.community.drive.controller.dto.DriveDownloadUrlResponse;
import com.nowcoder.community.drive.controller.dto.DriveEntryResponse;
import com.nowcoder.community.drive.controller.dto.DriveShareResponse;
import com.nowcoder.community.drive.controller.dto.VerifyDriveShareRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api/drive/shares")
public class DrivePublicShareController {

    private final DriveShareApplicationService shareApplicationService;

    public DrivePublicShareController(DriveShareApplicationService shareApplicationService) {
        this.shareApplicationService = shareApplicationService;
    }

    @GetMapping("/{shareToken}")
    public Result<DriveShareResponse> loadPublicShare(@PathVariable String shareToken) {
        return Result.ok(DriveShareResponse.from(shareApplicationService.loadPublicShare(shareToken)));
    }

    @PostMapping("/{shareToken}/verify")
    public Result<DriveShareResponse> verifyShare(
            @PathVariable String shareToken,
            @Valid @RequestBody VerifyDriveShareRequest request,
            HttpServletRequest httpRequest
    ) {
        return Result.ok(DriveShareResponse.from(shareApplicationService.verifyShare(
                new VerifyDriveShareCommand(shareToken, request.getPassword(), visitorFingerprint(httpRequest))
        )));
    }

    @GetMapping("/{shareToken}/entries")
    public Result<List<DriveEntryResponse>> listShareEntries(
            @PathVariable String shareToken,
            @RequestParam(value = "ticket", required = false) String ticket,
            @RequestParam(value = "parentId", required = false) String parentId
    ) {
        return Result.ok(DriveEntryResponse.from(shareApplicationService.listShareEntries(
                shareToken,
                ticket,
                parseUuidOrNull(parentId, "parentId")
        )));
    }

    @GetMapping("/{shareToken}/download-url")
    public Result<DriveDownloadUrlResponse> getDownloadUrl(
            @PathVariable String shareToken,
            @RequestParam(value = "ticket", required = false) String ticket,
            @RequestParam(value = "entryId", required = false) String entryId
    ) {
        return Result.ok(DriveDownloadUrlResponse.from(shareApplicationService.createShareDownloadUrl(
                shareToken,
                ticket,
                parseUuidOrNull(entryId, "entryId")
        )));
    }

    private static String visitorFingerprint(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ip = Objects.toString(request.getRemoteAddr(), "");
        String userAgent = Objects.toString(request.getHeader("User-Agent"), "");
        return ip + "|" + userAgent;
    }

    private static UUID parseUuidOrNull(String rawValue, String fieldName) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return UUID.fromString(rawValue.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(INVALID_ARGUMENT, fieldName + " 非法");
        }
    }
}
