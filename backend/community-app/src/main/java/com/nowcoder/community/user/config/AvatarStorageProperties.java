package com.nowcoder.community.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 头像/文件存储配置（自托管友好）。
 *
 * <p>约定：fileName/key 统一采用服务端生成的 {@code avatar/{userId}/{uuid}}，避免用户自定义路径带来的安全问题。</p>
 */
@ConfigurationProperties(prefix = "user.avatar")
public class AvatarStorageProperties {

    /**
     * 存储策略：local/r2
     */
    private String storage = "local";

    /**
     * local provider：文件落盘目录（建议为可持久化的 volume 挂载点）。
     */
    private String filesBaseDir = "/tmp/community-files";

    /**
     * local provider：对外访问 base URL（用于生成 headerUrl）。通常指向前端入口/反代入口（例如 http://localhost:12881）。
     */
    private String publicBaseUrl = "";

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getFilesBaseDir() {
        return filesBaseDir;
    }

    public void setFilesBaseDir(String filesBaseDir) {
        this.filesBaseDir = filesBaseDir;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
