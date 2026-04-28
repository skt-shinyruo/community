package com.nowcoder.community.user.controller;

import com.nowcoder.community.user.application.UserFileApplicationService;
import com.nowcoder.community.user.application.result.AvatarFileResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

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
        AvatarFileResult stored = userFileApplicationService.loadAvatarOrNull(uri);
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
