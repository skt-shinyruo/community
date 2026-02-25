# internal-token 轮转 Runbook（已废弃）

> ⚠️ SSOT=代码：当前实现已移除 `/internal/**` 的 header token 鉴权。
> 服务端不校验 `X-Internal-Token`，调用方也不再发送该 header。
> 因此 `*_INTERNAL_TOKEN` / `internal-token*` 相关配置不再影响 internal API 访问控制。

## 结论

- **无需轮转 internal-token：** 当前版本不再使用 internal-token 作为鉴权边界
- **安全边界建议：** 依赖网关显式拒绝 `/internal/**` + 部署网络隔离（服务端口不对外暴露）
- 若未来重新引入 internal token 机制：
  1) 必须先在服务端实现明确的 token 校验逻辑（并明确 401/403 语义）
  2) 再补齐调用方 header 注入、配置项、轮转窗口与契约测试
  3) 同步更新 `deploy/*` 与 `docs/*`，避免“发不验/验不发”的漂移
