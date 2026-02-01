# Task List: 产品体验闭环与可运营性提升（Self-host/Compose 友好）

Directory: `helloagents/plan/202601312154_product_readiness/`

---

## 0. Guardrails（全局约束 / 不可破坏）
- [√] 0.1 保持微服务边界：batch/read 能力由各域服务提供，不在 `gateway` 做业务聚合（verify how.md#ADR-003）
- [√] 0.2 部署形态：仅假设 Docker Compose；不引入 Kubernetes / service mesh 相关依赖与文档（verify how.md#technical-solution）
- [√] 0.3 安全默认：prod fail-closed；ops break-glass；dev-only 行为必须 profile 隔离（verify why.md#risk-assessment）

## 1. R1 Onboarding 闭环（auth-service + frontend + deploy）
- [√] 1.1 auth-service：新增 dev profile 默认配置（回传 activationLink/resetLink），file: `auth-service/src/main/resources/application-dev.yml`, verify why.md#scenario-r1-s1-dev-no-smtp
- [√] 1.2 auth-service：启动期安全校验（prod 环境禁止 exposeActivationLink/exposeResetLink=true），file: `auth-service/src/main/java/com/nowcoder/community/auth/config/AuthSecurityConfig.java` 或新增 `auth-service/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidation.java`, verify why.md#scenario-r1-s2-prod-secure-default
- [√] 1.3 auth-service：补齐/强化 prod 配置 fail-closed（禁止回传链接；mail 通道不可用需可观测），file: `auth-service/src/main/resources/application-prod.yml`, verify why.md#scenario-r1-s2-prod-secure-default
- [√] 1.4 frontend：RegisterView 根据是否返回 activationLink 做可行动提示与按钮链接，file: `frontend/src/views/RegisterView.vue`, verify why.md#requirement-r1-onboarding
- [√] 1.5 frontend：PasswordResetView 根据是否返回 resetLink 做可行动提示与按钮链接，file: `frontend/src/views/PasswordResetView.vue`, verify why.md#requirement-r1-onboarding
- [√] 1.6 deploy：补齐 env 示例（dev 回传开关、mail 可选策略），file: `deploy/.env.example`, verify how.md#配置清单（建议补齐到-deploy-env-example）
- [√] 1.7 deploy：更新自托管说明（无 SMTP 的 dev 闭环与 staging/prod 推荐），file: `deploy/README.md`, verify why.md#scenario-r1-s1-dev-no-smtp
- [?] 1.8 验收：dev 注册/找回密码 5 分钟内可完成闭环；prod 响应不包含 link（手工/脚本），verify why.md#requirement-r1-onboarding

## 2. R2 头像/文件自托管（user-service + gateway + frontend + deploy）
- [√] 2.1 user-service：增加存储配置（默认 local，兼容 qiniu），files: `user-service/src/main/java/com/nowcoder/community/user/config/AvatarStorageProperties.java`, `user-service/src/main/resources/application.yml`, verify why.md#requirement-r2-media
- [√] 2.2 user-service：抽象 AvatarStorageProvider 接口与选择器，files: `user-service/src/main/java/com/nowcoder/community/user/service/AvatarStorageProvider.java`, `user-service/src/main/java/com/nowcoder/community/user/service/AvatarService.java`, verify how.md#ADR-002
- [√] 2.3 user-service：实现 local filesystem 上传（multipart + size/mime 校验 + key 生成），file: `user-service/src/main/java/com/nowcoder/community/user/api/UserController.java`, verify why.md#scenario-r2-s1-compose-default
- [√] 2.4 user-service：实现 `/files/**` 只读访问（仅 avatar 前缀、防路径穿越），file: `user-service/src/main/java/com/nowcoder/community/user/api/FilesController.java`, verify how.md#GET-files
- [√] 2.5 user-service：兼容现有 qiniu 直传（保持 upload-token + ticket 流程），file: `user-service/src/main/java/com/nowcoder/community/user/service/AvatarService.java`, verify why.md#scenario-r2-s2-qiniu-optional
- [√] 2.6 gateway：增加 `/files/** -> lb://user-service` 路由并放行匿名 GET，file: `gateway/src/main/resources/application.yml`, verify how.md#本地文件读路径（必须补齐）
- [√] 2.7 frontend：SettingsView 支持 local/qiniu 两种上传契约（uploadUrl 或 uploadToken），file: `frontend/src/views/SettingsView.vue`, verify why.md#scenario-r2-s1-compose-default
- [√] 2.8 deploy：增加 user-service 文件 volume 与 env（USER_FILES_BASE_DIR/USER_PUBLIC_BASE_URL），file: `deploy/docker-compose.yml`, verify how.md#Compose-落地（推荐）
- [√] 2.9 deploy：补齐自托管文档（默认 local；可选 MailHog/MinIO 的 compose 增量文件），files: `deploy/README.md`, `deploy/docker-compose.mailhog.yml`（新增）, verify how.md#Testing-and-Deployment
- [?] 2.10 验收：无七牛/无外网时仍可上传头像并在列表/个人页展示；非法路径/超大文件被拒绝，verify why.md#scenario-r2-s1-compose-default

## 3. R3 Feed 性能（batch API + 前端改造）
- [√] 3.1 user-service：新增对外 batch 用户摘要 DTO，file: `user-service/src/main/java/com/nowcoder/community/user/api/dto/BatchUserSummaryRequest.java`, verify how.md#POST-api-users-batch-summary
- [√] 3.2 user-service：新增 `POST /api/users/batch-summary`（仅公开字段 + size limit），file: `user-service/src/main/java/com/nowcoder/community/user/api/UserController.java`, verify why.md#scenario-r3-s1-request-budget
- [√] 3.3 social-service：新增 `GET /api/likes/counts`（匿名可用 + size limit），file: `social-service/src/main/java/com/nowcoder/community/social/like/LikeController.java`, verify how.md#GET-api-likes-counts
- [√] 3.4 social-service：补齐 counts 查询实现（mapper/repo 支持批量），file: `social-service/src/main/java/com/nowcoder/community/social/like/LikeMapper.java`, verify why.md#scenario-r3-s1-request-budget
- [√] 3.5 social-service：新增 `GET /api/likes/statuses`（需要登录 + size limit），file: `social-service/src/main/java/com/nowcoder/community/social/like/LikeController.java`, verify how.md#GET-api-likes-statuses
- [√] 3.6 frontend：PostsView 改为 batch 补水与降级策略（请求预算 3~4），file: `frontend/src/views/PostsView.vue`, verify why.md#scenario-r3-s1-request-budget
- [√] 3.7 frontend：新增 Pinia 缓存 store（TTL=60s，用户摘要/点赞计数），file: `frontend/src/stores/postMetaCache.js`（新增）, verify why.md#scenario-r3-s2-cache-degrade
- [?] 3.8 验收：帖子列表从“逐条补水”收敛到“少量 batch 请求”；弱网下仍可滚动与交互，verify why.md#requirement-r3-feed

## 4. R4 运维可用性（Ops Console）
- [√] 4.1 frontend：修复 SearchView reindex 展示字段（indexedCount），file: `frontend/src/views/SearchView.vue`, verify why.md#scenario-r4-s1-reindex-console
- [√] 4.2 frontend：在 SearchView 重建索引弹窗加入 `X-Ops-Token` 输入并随请求发送，file: `frontend/src/views/SearchView.vue`, verify how.md#Ops-Console（frontend-gateway-search-service）
- [√] 4.3 frontend：新增 OpsConsoleView（集中高风险动作 + 配置引导，不回显 token），file: `frontend/src/views/OpsConsoleView.vue`（新增）, verify why.md#scenario-r4-s1-reindex-console
- [√] 4.4 frontend：路由与权限矩阵（仅 ADMIN 可见 ops 页面），file: `frontend/src/router/index.js`, verify why.md#requirement-r4-ops
- [√] 4.5 deploy：补齐 ops 配置说明（enabled/allowlist/token 与风险提示），file: `deploy/README.md`, verify how.md#配置清单（建议补齐到-deploy-env-example）
- [?] 4.6 验收：管理员能自助完成 reindex；失败时 UI 给出可行动提示（开关/allowlist/token），verify why.md#scenario-r4-s1-reindex-console

## 5. R5 角色与权限可运营（bootstrap + 管理入口）
- [√] 5.1 scripts：新增 bootstrap-admin 脚本（按 username/email 提升/降级；结构化日志审计），file: `scripts/bootstrap-admin.sh`（新增）, verify why.md#scenario-r5-s2-bootstrap
- [√] 5.2 user-service：新增 admin-only 角色变更 DTO（包含 targetUserId/type/reason/confirm），file: `user-service/src/main/java/com/nowcoder/community/user/api/dto/UpdateUserRoleRequest.java`（新增）, verify why.md#scenario-r5-s1-admin-manage
- [√] 5.3 user-service：新增 admin-only 角色变更 API（二次确认 + 审计日志 + 防误操作），file: `user-service/src/main/java/com/nowcoder/community/user/api/AdminUserController.java`（新增）, verify how.md#ADR-004
- [√] 5.4 frontend：新增 UserManagementView（搜索用户 + 修改角色 + 二次确认），file: `frontend/src/views/UserManagementView.vue`（新增）, verify why.md#scenario-r5-s1-admin-manage
- [√] 5.5 frontend：路由与权限矩阵（仅 ADMIN 可见用户管理页），file: `frontend/src/router/index.js`, verify why.md#requirement-r5-role
- [?] 5.6 验收：无 ADMIN 时可通过脚本 bootstrap；有 ADMIN 时可在 UI 受控管理且可追溯，verify why.md#requirement-r5-role

## 6. Security Check
- [√] 6.1 安全检查：dev-only 行为不进入 prod；文件上传安全（key/目录/大小/mime）；batch API 鉴权/限流；角色变更审计；ops break-glass 默认关闭且可解释（verify how.md#Security-and-Performance）

## 7. Documentation Update
- [√] 7.1 更新知识库（auth onboarding）：file: `helloagents/wiki/modules/auth-service.md`, verify why.md#requirement-r1-onboarding
- [√] 7.2 更新知识库（avatar/files）：file: `helloagents/wiki/modules/user.md`, verify why.md#requirement-r2-media
- [√] 7.3 更新知识库（batch likes & feed）：file: `helloagents/wiki/modules/social.md`, verify why.md#requirement-r3-feed
- [√] 7.4 更新知识库（gateway /files 路由）：file: `helloagents/wiki/modules/gateway.md`, verify why.md#requirement-r2-media
- [√] 7.5 更新知识库（ops break-glass & console）：files: `helloagents/wiki/runbooks/internal-ops.md`, `helloagents/wiki/modules/search.md`, verify why.md#requirement-r4-ops
- [√] 7.6 更新知识库（前端页面入口与权限矩阵）：file: `helloagents/wiki/modules/frontend.md`, verify why.md#requirement-r5-role

## 8. Testing
- [√] 8.1 smoke：dev 注册/找回密码闭环脚本（可复用现有 smoke-i0-auth.sh 思路），file: `scripts/smoke-i0-auth.sh`, verify why.md#requirement-r1-onboarding
- [√] 8.2 smoke：头像上传/读取脚本（local provider），file: `scripts/smoke-i1-avatar.sh`（新增）, verify why.md#scenario-r2-s1-compose-default
- [√] 8.3 smoke：feed 列表请求预算检查（浏览器 network 验证步骤写入脚本/文档），file: `helloagents/wiki/runbooks/security.md` 或新增 runbook, verify why.md#scenario-r3-s1-request-budget
