package com.nowcoder.community.drive.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.drive.application.DriveEntryApplicationService;
import com.nowcoder.community.drive.application.DriveShareApplicationService;
import com.nowcoder.community.drive.application.DriveSpaceApplicationService;
import com.nowcoder.community.drive.application.DriveTrashApplicationService;
import com.nowcoder.community.drive.application.DriveUploadApplicationService;
import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.controller.dto.CreateDriveFolderRequest;
import com.nowcoder.community.drive.controller.dto.CreateDriveShareRequest;
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
    public Result<DriveSpaceApplicationService.DriveSpaceResult> getSpace(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(spaceApplicationService.getSpace(userId));
    }

    @GetMapping("/entries")
    public Result<List<DriveEntryResult>> listEntries(
            Authentication authentication,
            @RequestParam(value = "parentId", required = false) String parentId
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(entriesOrEmpty(entryApplicationService.listEntries(userId, parseUuidOrNull(parentId, "parentId"))));
    }

    @GetMapping("/trash")
    public Result<List<DriveEntryResult>> listTrash(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(entriesOrEmpty(trashApplicationService.listTrash(userId)));
    }

    @GetMapping("/search")
    public Result<List<DriveEntryResult>> search(
            Authentication authentication,
            @RequestParam(value = "q", required = false) String keyword
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(entriesOrEmpty(entryApplicationService.search(userId, keyword)));
    }

    @PostMapping("/folders")
    public Result<DriveEntryResult> createFolder(Authentication authentication, @Valid @RequestBody CreateDriveFolderRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(entryApplicationService.createFolder(
                new DriveEntryApplicationService.CreateFolderCommand(
                        userId,
                        parseUuidOrNull(request.parentId(), "parentId"),
                        request.name()
                )
        ));
    }

    @PostMapping("/uploads")
    public Result<DriveUploadApplicationService.UploadSessionResult> prepareUpload(
            Authentication authentication,
            @Valid @RequestBody PrepareDriveUploadRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(uploadApplicationService.prepareUpload(
                new DriveUploadApplicationService.PrepareUploadCommand(
                        userId,
                        parseUuidOrNull(request.parentId(), "parentId"),
                        request.fileName(),
                        request.contentType(),
                        request.contentLength(),
                        request.checksumSha256()
                )
        ));
    }

    @PostMapping(value = "/uploads/{uploadId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DriveEntryResult> completeUpload(
            Authentication authentication,
            @PathVariable UUID uploadId,
            @RequestParam("fileKey") String fileKey,
            @RequestParam("file") MultipartFile file
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        DriveUploadContent content = new DriveUploadContent(file::getInputStream, file.getContentType(), file.getSize());
        return Result.ok(uploadApplicationService.completeUpload(new DriveUploadApplicationService.CompleteUploadCommand(
                userId,
                uploadId,
                content
        )));
    }

    @PostMapping("/entries/{entryId}/rename")
    public Result<DriveEntryResult> rename(
            Authentication authentication,
            @PathVariable UUID entryId,
            @Valid @RequestBody RenameDriveEntryRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(entryApplicationService.rename(
                new DriveEntryApplicationService.RenameCommand(userId, entryId, request.newName())
        ));
    }

    @PostMapping("/entries/{entryId}/move")
    public Result<DriveEntryResult> move(
            Authentication authentication,
            @PathVariable UUID entryId,
            @Valid @RequestBody MoveDriveEntryRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(entryApplicationService.move(
                new DriveEntryApplicationService.MoveCommand(
                        userId,
                        entryId,
                        parseUuidOrNull(request.targetParentId(), "targetParentId")
                )
        ));
    }

    @PostMapping("/entries/{entryId}/trash")
    public Result<DriveEntryResult> trash(Authentication authentication, @PathVariable UUID entryId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(trashApplicationService.trash(userId, entryId));
    }

    @PostMapping("/trash/{entryId}/restore")
    public Result<DriveEntryResult> restore(
            Authentication authentication,
            @PathVariable UUID entryId,
            @RequestBody(required = false) RestoreDriveEntryRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        String targetParentId = request == null ? null : request.targetParentId();
        return Result.ok(trashApplicationService.restore(
                userId,
                entryId,
                parseUuidOrNull(targetParentId, "targetParentId")
        ));
    }

    @DeleteMapping("/trash/{entryId}")
    public Result<Void> deletePermanently(Authentication authentication, @PathVariable UUID entryId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        trashApplicationService.deletePermanently(userId, entryId);
        return Result.ok();
    }

    @GetMapping("/entries/{entryId}/download-url")
    public Result<DriveDownloadUrlResult> getDownloadUrl(Authentication authentication, @PathVariable UUID entryId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(entryApplicationService.createDownloadUrl(userId, entryId));
    }

    @PostMapping("/entries/{entryId}/shares")
    public Result<DriveShareApplicationService.ShareResult> createShare(
            Authentication authentication,
            @PathVariable UUID entryId,
            @Valid @RequestBody CreateDriveShareRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(shareApplicationService.createShare(
                new DriveShareApplicationService.CreateShareCommand(
                        userId,
                        entryId,
                        request.password(),
                        request.expiresAt()
                )
        ));
    }

    @GetMapping("/shares")
    public Result<DriveShareApplicationService.SharePageResult> listOwnShares(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(shareApplicationService.listOwnShares(userId, page, size));
    }

    @DeleteMapping("/shares/{shareId}")
    public Result<Void> revokeShare(Authentication authentication, @PathVariable UUID shareId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        shareApplicationService.revokeShare(userId, shareId);
        return Result.ok();
    }

    private static List<DriveEntryResult> entriesOrEmpty(List<DriveEntryResult> entries) {
        return entries == null || entries.isEmpty() ? List.of() : entries.stream().toList();
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
