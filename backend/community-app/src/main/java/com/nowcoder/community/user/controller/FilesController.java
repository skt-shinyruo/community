package com.nowcoder.community.user.controller;

import com.nowcoder.community.user.application.UserFileApplicationService;
import com.nowcoder.community.user.application.result.AvatarFileResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
        AvatarFileResult stored = userFileApplicationService.loadAvatarOrNull(resolveFileKey(request));
        if (stored == null) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaType.parseMediaType(stored.contentType());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
        headers.set("X-Content-Type-Options", "nosniff");

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().headers(headers);
        if (stored.contentLength() >= 0) {
            builder.contentLength(stored.contentLength());
        }
        return builder.body(new InputStreamResource(stored.content()));
    }

    private String resolveFileKey(HttpServletRequest request) {
        String uri = request == null ? "" : request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return "";
        }
        String prefix = "/files/";
        int idx = uri.indexOf(prefix);
        if (idx < 0) {
            return "";
        }
        String raw = uri.substring(idx + prefix.length());
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
}
