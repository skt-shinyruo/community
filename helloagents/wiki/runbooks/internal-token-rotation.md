# internal-token 轮转 Runbook

## 目标
- 在不影响服务间 internal 调用的前提下轮转 `X-Internal-Token`。
- 将风险控制在“单服务爆炸半径”内（按服务 token），避免全局 `INTERNAL_TOKEN` 带来的扩大影响。

## 背景与机制（SSOT=代码）
- internal 接口统一以 `/internal/<segment>/**` 作为路径前缀。
- `common` 模块的 `InternalTokenFilter` 会对 `/internal/**` 强制校验 `X-Internal-Token`。
- token 查找优先级（按 segment）：`<segment>.internal-token` → `<segment>.internal-token-previous` →（兼容 alias）→ `user.internal-token` / `user.internal-token-previous`（仅当 segment 为 `users`）。

> 注意：当前 `InternalTokenFilter` **不再读取** `internal.token`（即使设置环境变量 `INTERNAL_TOKEN` 也不会被接受）。仍建议在生产环境逐步移除该全局 env，避免误解与错误配置扩散。

## 轮转流程（推荐）
以轮转 social-service 的 token 为例（segment= `social`）：

### Step 0：准备
1. 生成新 token（建议随机 32+ 字节，避免可猜测）。
2. 确认所有调用方（content-service/message-service/user-service 等）都通过配置注入 token，而非硬编码。

### Step 1：服务端进入灰度窗口（current + previous）
在 **目标服务**（例如 social-service）的配置中：
- 设置 `social.internal-token` = `NEW_TOKEN`
- 设置 `social.internal-token-previous` = `OLD_TOKEN`

部署/重启 social-service，使其同时接受新旧 token（不中断调用）。

### Step 2：逐步升级调用方（caller）
按调用链路逐个升级调用方配置（例如 content-service 的 `clients.social.internal-token`）：
- 将调用方发送的 `X-Internal-Token` 切换为 `NEW_TOKEN`
- 观察调用方错误率/延迟与目标服务的 internal 403 是否下降

### Step 3：收尾（移除 previous）
当确认所有调用方已切换到 `NEW_TOKEN` 后：
- 清空 `social.internal-token-previous`（或删除该配置项）
- 保留 `social.internal-token` = `NEW_TOKEN`

## 回滚策略
若升级调用方后出现大量 403 或调用失败：
1. 确认目标服务仍保留 `social.internal-token-previous = OLD_TOKEN`。
2. 将调用方 token 临时回滚到 `OLD_TOKEN`（快速恢复）。
3. 排查调用方配置分发是否生效、是否存在多套配置源（例如 Nacos 与本地 profile 叠加）。

## 验证清单
- [ ] 目标服务 `/internal/<segment>/**` 能被旧 token 与新 token 访问（灰度窗口阶段）。
- [ ] 调用方切换 token 后，internal 调用 403 不上升，且延迟无异常增长。
- [ ] 灰度结束后，旧 token 访问会被拒绝（403），新 token 正常。
- [ ] 生产环境不再依赖全局 `INTERNAL_TOKEN`（逐步收敛）。
