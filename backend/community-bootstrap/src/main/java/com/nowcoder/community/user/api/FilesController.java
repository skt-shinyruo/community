package com.nowcoder.community.user.api;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.user.service.AvatarStorageRouter;
import com.nowcoder.community.user.service.StoredAvatar;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.regex.Pattern;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

/**
 * 文件访问（仅头像）：/files/avatar/{userId}/{uuid}
 */
@RestController
public class FilesController {

    private static final Pattern AVATAR_KEY_PATTERN = Pattern.compile("^avatar/\\d+/[0-9a-fA-F]{32}$");

    private final AvatarStorageRouter router;

    public FilesController(AvatarStorageRouter router) {
        this.router = router;
    }

    @GetMapping("/files/**")
    public ResponseEntity<Resource> get(HttpServletRequest request) {
        String key = resolveKey(request);
        if (!StringUtils.hasText(key) || !AVATAR_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }

        StoredAvatar stored = router.currentProviderOrThrow().loadOrNull(key);
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

    private String resolveKey(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String uri = request.getRequestURI();
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
        // URL 解码：保持 / 分隔符语义
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
}
