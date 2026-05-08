package com.nowcoder.community.oss.controller;

import com.nowcoder.community.oss.application.ObjectQueryApplicationService;
import com.nowcoder.community.oss.application.result.ObjectDownloadResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
public class PublicFileController {

    private final ObjectQueryApplicationService queryApplicationService;

    public PublicFileController(ObjectQueryApplicationService queryApplicationService) {
        this.queryApplicationService = queryApplicationService;
    }

    @GetMapping("/files/**")
    public ResponseEntity<Resource> get(HttpServletRequest request) {
        ObjectDownloadResult download = queryApplicationService.resolvePublicFile(resolveFilePath(request));
        if (download == null) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.set("X-Content-Type-Options", "nosniff");
        if (StringUtils.hasText(download.cacheControl())) {
            headers.setCacheControl(download.cacheControl());
        }
        if (StringUtils.hasText(download.etag())) {
            headers.setETag(quoteEtag(download.etag()));
        }
        if (StringUtils.hasText(download.fileName())) {
            headers.setContentDisposition(ContentDisposition.inline().filename(download.fileName()).build());
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().headers(headers);
        if (download.contentLength() >= 0) {
            builder.contentLength(download.contentLength());
        }
        return builder.body(new InputStreamResource(download.content()));
    }

    private String resolveFilePath(HttpServletRequest request) {
        String uri = request == null ? "" : request.getRequestURI();
        String prefix = "/files/";
        int index = uri.indexOf(prefix);
        if (index < 0) {
            return "";
        }
        return URLDecoder.decode(uri.substring(index + prefix.length()), StandardCharsets.UTF_8);
    }

    private String quoteEtag(String etag) {
        String normalized = etag.trim();
        if (normalized.startsWith("\"") || normalized.startsWith("W/\"")) {
            return normalized;
        }
        return "\"" + normalized + "\"";
    }
}
