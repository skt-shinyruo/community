# Avatar/File Storage: Replace Qiniu with Cloudflare R2 (Design)

**Date:** 2026-03-11  
**Status:** Approved（用户确认按方案 1：服务端代传 R2 + `/files/**` 统一读取）

## Context / Problem

当前头像/文件存储在 `community-bootstrap` 中通过 `user.avatar.storage` 进行策略选择：

- `local`：服务端接收 multipart 并落盘到本地目录（`USER_FILES_BASE_DIR`），通过 `/files/**` 对外提供访问。
- `qiniu`：客户端直传对象存储（返回 uploadToken/bucketUrl），前端包含直传逻辑，后端依赖七牛 SDK 与 `QINIU_*` 配置。

需求变更：

1. 删除七牛云相关实现与配置。
2. 使用 Cloudflare R2 替代对象存储。
3. 上传链路采用 **B：服务端代传**（前端依旧 multipart → 后端 → 存储），以最小化前端改动与部署复杂度。
4. 访问链路采用 **统一 `/files/**`**：无论存储后端是 local 还是 R2，外部访问路径与 `headerUrl` 生成规则保持一致。

## Goals

1. 将头像/文件存储抽象为后端可替换的 API，并提供 `local` 与 `r2` 两种实现。
2. 保持对外接口稳定：
   - `/api/users/{id}/avatar/*` 不变；
   - `/files/**` 不变；
   - 前端不再关心对象存储直传细节。
3. 移除七牛依赖：
   - 删除 `qiniu-java-sdk` Maven 依赖；
   - 删除 `QINIU_*` 环境变量与 `qiniu.*` 配置；
   - 删除后端 `Qiniu*` 类与前端 qiniu 直传逻辑。
4. R2 模式下后端通过 S3 兼容 API 存储与读取对象，但 **不暴露** R2 公网 URL；仍由后端统一输出 `/files/**`。

## Non-goals

1. 不引入“客户端直传 R2 / presigned URL”方案（后续如需可另开设计）。
2. 不为除头像以外的通用业务文件建立完整的多租户/权限/目录体系（本次仍只允许 `avatar/{userId}/{uuid}` key）。
3. 不引入 CDN/自定义域名访问优化；缓存策略保持现有 1 天（可后续调整）。

## Proposed Approach (Recommended: keep provider model, remove qiniu)

### 1) Storage Strategy

将 `user.avatar.storage` 的可选值收敛为：

- `local`：保留现有落盘 + `/files/**` 读取逻辑；
- `r2`：新增实现，通过 R2（S3 API）`PutObject` 写入、`GetObject` 读取，由后端 `/files/**` 统一输出。

### 2) Public URL Behavior (SSOT)

无论 `local` 还是 `r2`，`headerUrl` 统一写入：

- `${USER_PUBLIC_BASE_URL}/files/${fileName}`

其中 `fileName` 固定为服务端生成的安全 key：

- `avatar/{userId}/{uuid32}`

### 3) Backend API / Modules

保持现有对外 API：

- `GET /api/users/{userId}/avatar/upload-token`
  - 返回服务端生成的 `fileName`
  - 返回 `uploadUrl=/api/users/{userId}/avatar/upload`（服务端上传地址）
- `POST /api/users/{userId}/avatar/upload`（multipart）
- `PUT /api/users/{userId}/avatar`（写入 `headerUrl`）

后端抽象：

- 把当前 `AvatarStorageProvider` 作为“后端文件存储 API（avatar 子集）”的抽象入口。
- 移除 `qiniu` provider，并新增 `r2` provider。
- `FilesController` 从“只支持 local”升级为“按当前 storage provider 读取”，但仍强约束只允许 avatar key。

### 4) Configuration

保留：

- `USER_AVATAR_STORAGE=local|r2`
- `USER_FILES_BASE_DIR`（local 用）
- `USER_PUBLIC_BASE_URL`（两者都用）

新增 R2 必需配置（以 env 注入为主）：

- `R2_ENDPOINT`（S3 endpoint）
- `R2_ACCESS_KEY`
- `R2_SECRET_KEY`
- `R2_BUCKET_NAME`
- `R2_REGION`（默认 `auto`）
- `R2_PATH_STYLE`（默认 `true`）

并移除所有 `QINIU_*` 相关配置与依赖。

### 5) Frontend

前端删除 `qiniu` 直传逻辑：

- 上传统一走服务端 uploadUrl（multipart）。
- 预览 URL 统一通过 `/files/${fileName}`（符合“统一 `/files/**` 入口”的目标）。

## Data Flow

1. 前端请求 `GET /api/users/{id}/avatar/upload-token`
2. 后端返回 `fileName`（服务端生成）与 `uploadUrl`
3. 前端 multipart `POST uploadUrl` 上传文件到后端
4. 后端校验（大小/MIME/fileName/票据）后：
   - local：写入 `USER_FILES_BASE_DIR/avatar/...`
   - r2：PutObject 到 `R2_BUCKET_NAME`，key 为 `avatar/...`
5. 前端 `PUT /api/users/{id}/avatar`，后端生成 `headerUrl=${USER_PUBLIC_BASE_URL}/files/${fileName}` 并写入用户资料
6. 浏览器访问 `GET /files/avatar/...`：
   - local：读取磁盘并返回
   - r2：从 R2 拉取并流式返回

## Risks / Notes

1. **R2 SDK 选型与依赖体积**
   - 采用 AWS SDK v2 `s3` 客户端；确保仅引入必要模块，避免过度依赖膨胀。

2. **Content-Type**
   - local 目前通过文件头检测媒体类型；R2 优先使用 PutObject 设置的 content-type，若缺失可降级为现有检测逻辑或 `application/octet-stream`。

3. **/files 代理读取的成本**
   - 代理读取会增加后端带宽，但满足“统一入口 + 不暴露对象存储 URL”的安全/治理目标。

4. **Fail-closed**
   - 当 `USER_AVATAR_STORAGE=r2` 且 R2 配置不完整时：应明确报错（可在 prod profile 下进一步加启动期校验）。

## Acceptance Criteria

1. `USER_AVATAR_STORAGE=local`：行为与当前一致，`/files/**` 仍可访问本地头像。
2. `USER_AVATAR_STORAGE=r2`：
   - 头像上传成功后能通过 `/files/avatar/...` 访问到图片；
   - `headerUrl` 指向 `${USER_PUBLIC_BASE_URL}/files/...`，前端可直接展示。
3. 代码库中不再出现七牛相关内容：
   - 无 `qiniu-java-sdk` 依赖；
   - 无 `QINIU_*` 环境变量与配置；
   - 前端无 qiniu 直传逻辑分支。

## Verification (post-implementation)

- 后端单测：`mvn -pl backend/community-bootstrap -am test`
- compose 校验：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config`
- 本地手动验证：
  - 启动后上传头像并刷新页面；
  - 访问 `GET /files/avatar/{userId}/{uuid}` 返回 200 且 Content-Type 为图片类型。

