package com.nowcoder.community.drive.exception;

import com.nowcoder.community.common.exception.ErrorCode;

public enum DriveErrorCode implements ErrorCode {
    DRIVE_SPACE_NOT_FOUND(16001, "网盘空间不存在", 404),
    DRIVE_ENTRY_NOT_FOUND(16002, "网盘条目不存在", 404),
    DRIVE_PARENT_NOT_FOUND(16003, "目标文件夹不存在", 404),
    DRIVE_DUPLICATE_NAME(16004, "同名文件或文件夹已存在", 409),
    DRIVE_QUOTA_EXCEEDED(16005, "网盘容量不足", 409),
    DRIVE_INVALID_MOVE(16006, "不能移动到自身或子目录", 400),
    DRIVE_ENTRY_TRASHED(16007, "回收站条目不可执行该操作", 409),
    DRIVE_SHARE_INVALID(16008, "分享链接不可用", 404),
    DRIVE_SHARE_PASSWORD_INVALID(16009, "提取码错误", 403),
    DRIVE_UPLOAD_INVALID(16010, "上传会话不可用", 409),
    DRIVE_STORAGE_UNAVAILABLE(16011, "网盘存储服务不可用", 503);

    private final int code;
    private final String message;
    private final int httpStatus;

    DriveErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
