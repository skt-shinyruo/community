package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.PostMediaUploadContent;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;

public final class PostMediaUploadContentAdapter {

    private PostMediaUploadContentAdapter() {
    }

    public static PostMediaUploadContent from(MultipartFile file) {
        return new PostMediaUploadContent(
                () -> open(file),
                file == null ? "" : file.getContentType(),
                file == null ? 0 : file.getSize(),
                ""
        );
    }

    private static InputStream open(MultipartFile file) {
        if (file == null) {
            return InputStream.nullInputStream();
        }
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new BusinessException(INTERNAL_ERROR, "读取媒体文件失败", e);
        }
    }
}
