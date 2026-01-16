# Changelog

本文件记录项目（含知识库/架构/代码）的重要变更。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- Maven 多模块工程：`legacy-community`、`common`、`gateway`、`auth-service`。
- `common` 统一返回 `Result<T>` / 错误码 / 全局异常 / traceId Filter。
- `gateway`：JWT 验签 + 路由 `/api/auth/** -> auth-service` + CORS + traceId 透传。
- `auth-service`：`/api/auth/login|refresh|logout|me`（JWT access + refresh cookie 旋转，Redis 存储）。
- `frontend/`：Vue3 SPA（Vite + Router + Pinia + Axios）用于迭代 0 联调。
- 本地基础设施示例：`deploy/docker-compose.yml`（Nacos/MySQL/Redis）与 `deploy/.env.example`。
- CI 工作流：`.github/workflows/ci.yml`（backend build/test + frontend build）。
- 冒烟脚本：`scripts/smoke-i0-auth.sh`（登录→me→refresh→logout）。

### Changed
- 父工程升级到 Spring Boot 3.2.6 + Java 17，并加入 Maven Enforcer 门禁。
- `legacy-community` 完成 Jakarta 迁移与 Security 6 适配，迁移期默认不自动启动 Kafka Listener。

### Removed
- `legacy-community` 中的 Elasticsearch 旧实现（迁移期降级，后续迭代 1 由 `search-service` 重写）。

## [0.0.1] - 2026-01-16

### Added
- 初始化 HelloAGENTS 知识库骨架（`helloagents/`）。
- 新增“Boot 3 + Java 17 + Vue3 + Nacos 微服务化拆分”方案包（已执行并归档到 `helloagents/history/2026-01/202601161428_boot3_ms_vue3_nacos/`）。
