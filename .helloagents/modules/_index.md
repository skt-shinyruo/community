# 模块索引

> 通过此文件快速定位模块文档

## 模块清单

| 模块 | 职责 | 状态 | 文档 |
|------|------|------|------|
| common | 统一 Result/错误码/异常处理/traceId | ✅Stable | [common.md](./common.md) |
| gateway | 统一入口：路由/CORS/trace/错误收敛 | ✅Stable | [gateway.md](./gateway.md) |
| ops-service | 运维平面（ADMIN）：/api/ops/** | ✅Stable | [ops-service.md](./ops-service.md) |
| auth-service | 登录/刷新/登出闭环（JWT + refresh rotation） | ✅Stable | [auth-service.md](./auth-service.md) |
| user-service | 用户资料与头像 | ✅Stable | [user.md](./user.md) |
| content-service | 帖子/评论/热帖/敏感词过滤 | ✅Stable | [content.md](./content.md) |
| social-service | 点赞/关注/拉黑 | ✅Stable | [social.md](./social.md) |
| message-service | 通知/私信 | ✅Stable | [message.md](./message.md) |
| search-service | 搜索（ES + reindex） | ✅Stable | [search.md](./search.md) |
| analytics-service | 统计（UV/DAU） | ✅Stable | [analytics.md](./analytics.md) |
| frontend | Vue3 SPA | ✅Stable | [frontend.md](./frontend.md) |
| infra | 交付与跨服务基础设施（deploy/scripts/infra-*） | ✅Stable | [infra.md](./infra.md) |

## 模块依赖关系（高层）

```
frontend → gateway → {各业务服务}
各业务服务 → common / infra-*
跨服务同步调用 → *-api（Dubbo RPC 接口/DTO）
异步事件 → contracts-event-core / Kafka
```

## 状态说明
- ✅ Stable：已收敛并受门禁保护
- 🚧 Developing：开发中
- 📝 Planned：规划中
