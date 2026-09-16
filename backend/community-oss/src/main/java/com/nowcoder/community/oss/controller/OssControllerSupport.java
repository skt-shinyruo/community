package com.nowcoder.community.oss.controller;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

final class OssControllerSupport {

    private OssControllerSupport() {
    }

    static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value.trim());
    }

    static InputStream openUploadStream(MultipartFile file) {
        try {
            return file == null ? InputStream.nullInputStream() : file.getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read upload file", e);
        }
    }
}
