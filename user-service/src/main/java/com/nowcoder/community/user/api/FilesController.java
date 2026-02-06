package com.nowcoder.community.user.api;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.config.AvatarStorageProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.regex.Pattern;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

/**
 * 本地文件访问（仅头像）：/files/avatar/{userId}/{uuid}
 */
@RestController
@EnableConfigurationProperties(AvatarStorageProperties.class)
public class FilesController {

    private static final Pattern AVATAR_KEY_PATTERN = Pattern.compile("^avatar/\\d+/[0-9a-fA-F]{32}$");

    private final AvatarStorageProperties storageProperties;

    public FilesController(AvatarStorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @GetMapping("/files/**")
    public ResponseEntity<Resource> get(HttpServletRequest request) {
        if (!isLocalEnabled()) {
            return ResponseEntity.notFound().build();
        }

        String key = resolveKey(request);
        if (!StringUtils.hasText(key) || !AVATAR_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }

        Path base = resolveBaseDir();
        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            throw new BusinessException(INVALID_ARGUMENT, "fileKey 非法");
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = detectMediaType(target);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
        headers.set("X-Content-Type-Options", "nosniff");

        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(target));
    }

    private boolean isLocalEnabled() {
        if (storageProperties == null) {
            return false;
        }
        String storage = storageProperties.getStorage();
        return StringUtils.hasText(storage) && "local".equalsIgnoreCase(storage.trim());
    }

    private Path resolveBaseDir() {
        String baseDir = storageProperties.getFilesBaseDir();
        if (!StringUtils.hasText(baseDir)) {
            throw new BusinessException(INVALID_ARGUMENT, "filesBaseDir 未配置");
        }
        return Paths.get(baseDir).toAbsolutePath().normalize();
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

    private MediaType detectMediaType(Path target) {
        if (target == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try (InputStream in = Files.newInputStream(target)) {
            byte[] head = in.readNBytes(16);
            if (isJpeg(head)) {
                return MediaType.IMAGE_JPEG;
            }
            if (isPng(head)) {
                return MediaType.IMAGE_PNG;
            }
            if (isGif(head)) {
                return MediaType.IMAGE_GIF;
            }
            if (isWebp(head)) {
                return MediaType.valueOf("image/webp");
            }
        } catch (java.io.IOException ignored) {
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private boolean isJpeg(byte[] head) {
        return head != null && head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] head) {
        return head != null && head.length >= 8
                && (head[0] & 0xFF) == 0x89
                && head[1] == 0x50
                && head[2] == 0x4E
                && head[3] == 0x47
                && head[4] == 0x0D
                && head[5] == 0x0A
                && head[6] == 0x1A
                && head[7] == 0x0A;
    }

    private boolean isGif(byte[] head) {
        return head != null && head.length >= 6
                && head[0] == 'G'
                && head[1] == 'I'
                && head[2] == 'F'
                && head[3] == '8'
                && (head[4] == '7' || head[4] == '9')
                && head[5] == 'a';
    }

    private boolean isWebp(byte[] head) {
        return head != null && head.length >= 12
                && head[0] == 'R'
                && head[1] == 'I'
                && head[2] == 'F'
                && head[3] == 'F'
                && head[8] == 'W'
                && head[9] == 'E'
                && head[10] == 'B'
                && head[11] == 'P';
    }
}
