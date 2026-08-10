package com.nowcoder.community.drive.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.drive.application.DriveShareApplicationService;
import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    public Result<DriveShareApplicationService.PublicShareGateResult> loadPublicShare(@PathVariable String shareToken) {
        return Result.ok(shareApplicationService.loadPublicShareGate(shareToken));
    }

    @PostMapping("/{shareToken}/verify")
    public Result<DriveShareApplicationService.ShareResult> verifyShare(
            @PathVariable String shareToken,
            @Valid @RequestBody VerifyDriveShareRequest request,
            HttpServletRequest httpRequest
    ) {
        return Result.ok(shareApplicationService.verifyShare(
                new DriveShareApplicationService.VerifyShareCommand(
                        shareToken,
                        request.password(),
                        visitorFingerprint(httpRequest)
                )
        ));
    }

    @GetMapping("/{shareToken}/entries")
    public Result<List<DriveEntryResult>> listShareEntries(
            @PathVariable String shareToken,
            @RequestParam(value = "ticket", required = false) String ticket,
            @RequestParam(value = "parentId", required = false) String parentId
    ) {
        return Result.ok(entriesOrEmpty(shareApplicationService.listShareEntries(
                shareToken,
                ticket,
                parseUuidOrNull(parentId, "parentId")
        )));
    }

    @GetMapping("/{shareToken}/download-url")
    public Result<DriveDownloadUrlResult> getDownloadUrl(
            @PathVariable String shareToken,
            @RequestParam(value = "ticket", required = false) String ticket,
            @RequestParam(value = "entryId", required = false) String entryId
    ) {
        return Result.ok(shareApplicationService.createShareDownloadUrl(
                shareToken,
                ticket,
                parseUuidOrNull(entryId, "entryId")
        ));
    }

    private static List<DriveEntryResult> entriesOrEmpty(List<DriveEntryResult> entries) {
        return entries == null || entries.isEmpty() ? List.of() : entries.stream().toList();
    }

    private static String visitorFingerprint(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        byte[] ip = Objects.toString(request.getRemoteAddr(), "").getBytes(StandardCharsets.UTF_8);
        byte[] userAgent = Objects.toString(request.getHeader("User-Agent"), "").getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(ip.length).array());
            digest.update(ip);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(userAgent.length).array());
            return HexFormat.of().formatHex(digest.digest(userAgent));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
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
