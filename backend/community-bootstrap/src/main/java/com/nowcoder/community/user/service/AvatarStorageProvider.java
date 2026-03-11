package com.nowcoder.community.user.service;

import com.nowcoder.community.user.api.dto.AvatarUploadTokenResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 头像存储 provider 抽象：
 * - local：服务端接收 multipart 并落盘（返回 uploadUrl/uploadMethod）。
 * - r2：服务端接收 multipart 并写入对象存储（Cloudflare R2），并通过 /files/** 统一对外提供访问。
 */
public interface AvatarStorageProvider {

    /**
     * provider 名称（local/r2）。
     */
    String provider();

    /**
     * 生成上传所需信息（fileName 由服务端生成并透传给 provider）。
     */
    AvatarUploadTokenResponse createUploadToken(int userId, String fileName);

    /**
     * local provider：服务端接收上传并落盘。
     *
     * <p>若当前 provider 不支持服务端上传，应抛出业务异常。</p>
     */
    void upload(int userId, String fileName, MultipartFile file);

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
