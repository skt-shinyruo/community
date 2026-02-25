# community 知识库

> 本目录（`.helloagents/`）是项目知识库入口，供 HelloAGENTS 进行任务理解、方案设计与历史追溯。

## 快速导航

| 需要了解 | 读取文件 |
|---------|---------|
| 项目概况、技术栈、开发约定 | [context.md](context.md) |
| 项目技术约定（SSOT） | [project.md](project.md) |
| 项目概览（入口文档） | [overview.md](overview.md) |
| 架构设计 | [arch.md](arch.md) |
| API 手册 | [api.md](api.md) |
| 数据模型 | [data.md](data.md) |
| 模块索引 | [modules/_index.md](modules/_index.md) |
| 历史方案索引 | [archive/_index.md](archive/_index.md) |
| 当前待执行的方案 | [plan/](plan/) |
| 历史会话记录 | [sessions/](sessions/) |

## 模块关键词索引

> AI 可先读此表确定涉及模块，再按需深读对应模块文档。

| 模块 | 关键词 | 摘要 |
|------|--------|------|
| common | Result, ErrorCode, traceId, ExceptionHandler | 公共错误协议、trace 与运行期基础设施支撑 |
| gateway | Spring Cloud Gateway, CORS, Trace, OriginGuard | 统一入口与边界治理（透明转发为主） |
| ops-service | ops, reindex, outbox, admin | 运维平面（高风险操作隔离，/api/ops/**） |
| auth-service | JWT, refresh token, captcha, password reset | 登录/刷新/登出闭环与账号安全能力 |
| user | profile, avatar, qiniu, points | 用户资料、头像与成长体系 |
| content | post, comment, moderation, outbox | 帖子/评论与内容生命周期 |
| social | like, follow, block, projection | 点赞/关注/拉黑与投影 |
| message | notice, dm, kafka consumer | 通知/私信与消息消费链路 |
| search | elasticsearch, reindex, highlight | 搜索与索引运维 |
| analytics | UV, DAU, redis | 统计（UV/DAU）与采集链路 |
| frontend | Vue3, Vite, SPA | 前端 SPA（前端直连 gateway 为默认本地模式） |
| infra | deploy, docker compose, observability, starters | 交付与跨服务基础设施（deploy/scripts/infra-*） |

## 知识库状态

```yaml
kb_version: 2.2.9
最后更新: 2026-02-24 23:39
模块数量: 12
待执行方案: 1
```

## 读取指引

```yaml
启动任务:
  1. 读取本文件获取导航
  2. 读取 context.md 获取项目上下文
  3. 检查 plan/ 是否有进行中方案包

任务相关:
  - 涉及特定模块: 读取 modules/{模块名}.md
  - 需要历史决策: 先查 archive/_index.md → 再进入对应归档目录
  - 继续之前任务: 读取 plan/{方案包}/proposal.md 与 tasks.md
```
