package com.nowcoder.community.drive.exception;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

public enum DriveErrorCode implements ErrorCode {
    DRIVE_SPACE_NOT_FOUND(16001, "网盘空间不存在", ErrorKind.NOT_FOUND),
    DRIVE_ENTRY_NOT_FOUND(16002, "网盘条目不存在", ErrorKind.NOT_FOUND),
    DRIVE_PARENT_NOT_FOUND(16003, "目标文件夹不存在", ErrorKind.NOT_FOUND),
    DRIVE_DUPLICATE_NAME(16004, "同名文件或文件夹已存在", ErrorKind.CONFLICT),
    DRIVE_QUOTA_EXCEEDED(16005, "网盘容量不足", ErrorKind.CONFLICT),
    DRIVE_INVALID_MOVE(16006, "不能移动到自身或子目录", ErrorKind.INVALID_INPUT),
    DRIVE_ENTRY_TRASHED(16007, "回收站条目不可执行该操作", ErrorKind.CONFLICT),
    DRIVE_SHARE_INVALID(16008, "分享链接不可用", ErrorKind.NOT_FOUND),
    DRIVE_SHARE_PASSWORD_INVALID(16009, "提取码错误", ErrorKind.FORBIDDEN),
    DRIVE_UPLOAD_INVALID(16010, "上传会话不可用", ErrorKind.CONFLICT),
    DRIVE_STORAGE_UNAVAILABLE(16011, "网盘存储服务不可用", ErrorKind.UNAVAILABLE);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    DriveErrorCode(int code, String message, ErrorKind kind) {
        this.code = code;
        this.message = message;
        this.kind = kind;
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
    public ErrorKind getKind() {
        return kind;
    }
}
