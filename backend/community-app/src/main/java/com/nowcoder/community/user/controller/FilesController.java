package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.dto.AvatarFileResource;
import com.nowcoder.community.user.service.UserFileApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

/**
 * 文件访问（仅头像）：/files/avatar/{userId}/{uuid}
 */
@RestController
public class FilesController {

    private final UserFileApplicationService userFileApplicationService;

    public FilesController(UserFileApplicationService userFileApplicationService) {
        this.userFileApplicationService = userFileApplicationService;
    }

    @GetMapping("/files/**")
    public ResponseEntity<Resource> get(HttpServletRequest request) {
        String uri = request == null ? "" : request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }

        AvatarFileResource stored = userFileApplicationService.loadAvatarOrNull(uri);
        if (stored == null) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = stored.mediaType();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
        headers.set("X-Content-Type-Options", "nosniff");

        return ResponseEntity.ok().headers(headers).body(stored.resource());
    }
}
