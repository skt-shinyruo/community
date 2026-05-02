package com.nowcoder.community.user.infrastructure.avatar;

import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;

import java.util.UUID;

/**
 * 头像存储 provider 抽象：
 * - local：服务端接收上传内容并落盘（返回 uploadUrl/uploadMethod）。
 * - r2：服务端接收上传内容并写入对象存储（Cloudflare R2），并通过 /files/** 统一对外提供访问。
 */
public interface AvatarStorageProvider {

    /**
     * provider 名称（local/r2）。
     */
    String provider();

    /**
     * 生成上传所需信息（fileName 由服务端生成并透传给 provider）。
     */
    AvatarUploadTokenResult createUploadToken(UUID userId, String fileName);

    /**
     * local provider：服务端接收上传并落盘。
     *
     * <p>若当前 provider 不支持服务端上传，应抛出业务异常。</p>
     */
    void upload(UUID userId, String fileName, AvatarUploadContent content);

    /**
     * 生成头像可访问 URL（用于写入用户 headerUrl）。
     */
    String buildAvatarUrl(String fileName);

    /**
     * 读取存储中的文件（用于 /files/**）。
     *
     * <p>约定：key 为服务端生成的 {@code avatar/{userId}/{uuid}}。</p>
     *
     * @return 找不到时返回 null
     */
    StoredAvatar loadOrNull(String key);
}
