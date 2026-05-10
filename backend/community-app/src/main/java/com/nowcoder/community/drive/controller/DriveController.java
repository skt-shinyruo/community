package com.nowcoder.community.drive.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.drive.application.DriveEntryApplicationService;
import com.nowcoder.community.drive.application.DriveShareApplicationService;
import com.nowcoder.community.drive.application.DriveSpaceApplicationService;
import com.nowcoder.community.drive.application.DriveTrashApplicationService;
import com.nowcoder.community.drive.application.DriveUploadApplicationService;
import com.nowcoder.community.drive.application.command.CompleteDriveUploadCommand;
import com.nowcoder.community.drive.application.command.CreateDriveFolderCommand;
import com.nowcoder.community.drive.application.command.CreateDriveShareCommand;
import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.command.MoveDriveEntryCommand;
import com.nowcoder.community.drive.application.command.PrepareDriveUploadCommand;
import com.nowcoder.community.drive.application.command.RenameDriveEntryCommand;
import com.nowcoder.community.drive.controller.dto.CreateDriveFolderRequest;
import com.nowcoder.community.drive.controller.dto.CreateDriveShareRequest;
import com.nowcoder.community.drive.controller.dto.DriveDownloadUrlResponse;
import com.nowcoder.community.drive.controller.dto.DriveEntryResponse;
import com.nowcoder.community.drive.controller.dto.DriveShareResponse;
import com.nowcoder.community.drive.controller.dto.DriveSpaceResponse;
import com.nowcoder.community.drive.controller.dto.DriveUploadSessionResponse;
import com.nowcoder.community.drive.controller.dto.MoveDriveEntryRequest;
import com.nowcoder.community.drive.controller.dto.PrepareDriveUploadRequest;
import com.nowcoder.community.drive.controller.dto.RenameDriveEntryRequest;
import com.nowcoder.community.drive.controller.dto.RestoreDriveEntryRequest;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api/drive")
public class DriveController {

    private final DriveSpaceApplicationService spaceApplicationService;
    private final DriveEntryApplicationService entryApplicationService;
    private final DriveUploadApplicationService uploadApplicationService;
    private final DriveTrashApplicationService trashApplicationService;
    private final DriveShareApplicationService shareApplicationService;

    public DriveController(
            DriveSpaceApplicationService spaceApplicationService,
            DriveEntryApplicationService entryApplicationService,
            DriveUploadApplicationService uploadApplicationService,
            DriveTrashApplicationService trashApplicationService,
            DriveShareApplicationService shareApplicationService
    ) {
        this.spaceApplicationService = spaceApplicationService;
        this.entryApplicationService = entryApplicationService;
        this.uploadApplicationService = uploadApplicationService;
        this.trashApplicationService = trashApplicationService;
        this.shareApplicationService = shareApplicationService;
    }

    @GetMapping("/space")
    public Result<DriveSpaceResponse> getSpace(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveSpaceResponse.from(spaceApplicationService.getSpace(userId)));
    }

    @GetMapping("/entries")
    public Result<List<DriveEntryResponse>> listEntries(
            Authentication authentication,
            @RequestParam(value = "parentId", required = false) String parentId
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveEntryResponse.from(entryApplicationService.listEntries(userId, parseUuidOrNull(parentId, "parentId"))));
    }

    @GetMapping("/trash")
    public Result<List<DriveEntryResponse>> listTrash(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveEntryResponse.from(trashApplicationService.listTrash(userId)));
    }

    @GetMapping("/search")
    public Result<List<DriveEntryResponse>> search(
            Authentication authentication,
            @RequestParam(value = "q", required = false) String keyword
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveEntryResponse.from(entryApplicationService.search(userId, keyword)));
    }

    @PostMapping("/folders")
    public Result<DriveEntryResponse> createFolder(Authentication authentication, @Valid @RequestBody CreateDriveFolderRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveEntryResponse.from(entryApplicationService.createFolder(
                new CreateDriveFolderCommand(userId, parseUuidOrNull(request.getParentId(), "parentId"), request.getName())
        )));
    }

    @PostMapping("/uploads")
    public Result<DriveUploadSessionResponse> prepareUpload(Authentication authentication, @Valid @RequestBody PrepareDriveUploadRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveUploadSessionResponse.from(uploadApplicationService.prepareUpload(
                new PrepareDriveUploadCommand(
                        userId,
                        parseUuidOrNull(request.getParentId(), "parentId"),
                        request.getFileName(),
                        request.getContentType(),
                        request.getContentLength(),
                        request.getChecksumSha256()
                )
        )));
    }

    @PostMapping(value = "/uploads/{uploadId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DriveEntryResponse> completeUpload(
            Authentication authentication,
            @PathVariable UUID uploadId,
            @RequestParam("fileKey") String fileKey,
            @RequestParam("file") MultipartFile file
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        DriveUploadContent content = new DriveUploadContent(file::getInputStream, file.getContentType(), file.getSize(), "");
        return Result.ok(DriveEntryResponse.from(uploadApplicationService.completeUpload(new CompleteDriveUploadCommand(
                userId,
                uploadId,
                content
        ))));
    }

    @PostMapping("/entries/{entryId}/rename")
    public Result<DriveEntryResponse> rename(
            Authentication authentication,
            @PathVariable UUID entryId,
            @Valid @RequestBody RenameDriveEntryRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveEntryResponse.from(entryApplicationService.rename(
                new RenameDriveEntryCommand(userId, entryId, request.getNewName())
        )));
    }

    @PostMapping("/entries/{entryId}/move")
    public Result<DriveEntryResponse> move(
            Authentication authentication,
            @PathVariable UUID entryId,
            @Valid @RequestBody MoveDriveEntryRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveEntryResponse.from(entryApplicationService.move(
                new MoveDriveEntryCommand(userId, entryId, parseUuidOrNull(request.getTargetParentId(), "targetParentId"))
        )));
    }

    @PostMapping("/entries/{entryId}/trash")
    public Result<DriveEntryResponse> trash(Authentication authentication, @PathVariable UUID entryId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveEntryResponse.from(trashApplicationService.trash(userId, entryId)));
    }

    @PostMapping("/trash/{entryId}/restore")
    public Result<DriveEntryResponse> restore(
            Authentication authentication,
            @PathVariable UUID entryId,
            @RequestBody(required = false) RestoreDriveEntryRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        String targetParentId = request == null ? null : request.getTargetParentId();
        return Result.ok(DriveEntryResponse.from(trashApplicationService.restore(
                userId,
                entryId,
                parseUuidOrNull(targetParentId, "targetParentId")
        )));
    }

    @DeleteMapping("/trash/{entryId}")
    public Result<Void> deletePermanently(Authentication authentication, @PathVariable UUID entryId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        trashApplicationService.deletePermanently(userId, entryId);
        return Result.ok();
    }

    @GetMapping("/entries/{entryId}/download-url")
    public Result<DriveDownloadUrlResponse> getDownloadUrl(Authentication authentication, @PathVariable UUID entryId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveDownloadUrlResponse.from(entryApplicationService.createDownloadUrl(userId, entryId)));
    }

    @PostMapping("/entries/{entryId}/shares")
    public Result<DriveShareResponse> createShare(
            Authentication authentication,
            @PathVariable UUID entryId,
            @Valid @RequestBody CreateDriveShareRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(DriveShareResponse.from(shareApplicationService.createShare(
                new CreateDriveShareCommand(userId, entryId, request.getPassword(), request.getExpiresAt())
        )));
    }

    @DeleteMapping("/shares/{shareId}")
    public Result<Void> revokeShare(Authentication authentication, @PathVariable UUID shareId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        shareApplicationService.revokeShare(userId, shareId);
        return Result.ok();
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
