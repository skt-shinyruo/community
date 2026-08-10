package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.PostMediaApplicationService;
import com.nowcoder.community.content.application.PostMediaUploadContent;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.controller.dto.PostMediaUploadSessionResponse;
import com.nowcoder.community.content.controller.dto.PreparePostMediaUploadRequest;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;

@RestController
@RequestMapping("/api/posts/media")
public class PostMediaController {

    private final PostMediaApplicationService applicationService;

    public PostMediaController(PostMediaApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/upload-sessions")
    public Result<PostMediaUploadSessionResponse> prepareUpload(
            Authentication authentication,
            @Valid @RequestBody PreparePostMediaUploadRequest request
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        PostMediaUploadSessionResult result = applicationService.prepareUpload(new PostMediaApplicationService.PreparePostMediaUploadCommand(
                actorUserId,
                request.requestId(),
                request.fileName(),
                request.contentType(),
                request.contentLength(),
                request.mediaKind(),
                request.checksumSha256()
        ));
        return Result.ok(PostMediaUploadSessionResponse.from(result));
    }

    @PostMapping(value = "/{assetId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> upload(
            Authentication authentication,
            @PathVariable UUID assetId,
            @RequestParam UUID uploadId,
            @RequestParam("file") MultipartFile file
    ) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        applicationService.completeUpload(actorUserId, assetId, uploadId, toUploadContent(file));
        return Result.ok();
    }

    private PostMediaUploadContent toUploadContent(MultipartFile file) {
        return new PostMediaUploadContent(
                () -> openUploadStream(file),
                file == null ? "" : file.getContentType(),
                file == null ? 0 : file.getSize(),
                ""
        );
    }

    private InputStream openUploadStream(MultipartFile file) {
        if (file == null) {
            return InputStream.nullInputStream();
        }
        try {
            return file.getInputStream();
        } catch (IOException error) {
            throw new BusinessException(INTERNAL_ERROR, "读取媒体文件失败", error);
        }
    }
}
