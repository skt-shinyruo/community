# task

目标：移除 social 写路径对 content-service 的同步回源兜底，强制仅依赖本地投影（缺失/不完整 fail-closed），从结构上避免形成跨服务同步依赖环与级联故障放大。

- [√] 1. social-service：删除 `ContentEntityResolver` 回源逻辑与配置项（`social.entity-resolve.fallback.*`），投影缺失/不完整直接返回 503（fail-closed）。
- [√] 2. social-service：移除 `ContentServiceClient`（避免残留同步依赖入口）。
- [√] 3. 知识库：更新 `.helloagents/arch.md`、`.helloagents/modules/social.md`、`.helloagents/modules/content.md`、`.helloagents/api.md`，移除“受控回源/迁移期兜底”描述并明确 fail-closed。
- [√] 4. 验证：运行 `mvn test -pl social-service -am`。
- [√] 5. 记录：更新 `.helloagents/CHANGELOG.md`；迁移本 solution package 到 `.helloagents/archive/2026-02/` 并更新 `.helloagents/archive/_index.md`。
- [√] 6. 构建解耦：移除 `social-service` 对 `content-api` 的 Maven 依赖，进一步降低编译期耦合与误用入口。
