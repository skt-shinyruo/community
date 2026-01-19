# Change Proposal: 移除 legacy-community 模块（微服务终局）

## Requirement Background

当前仓库已经以“微服务 + 前后端分离”为主运行路径（`deploy/docker-compose.yml` + `gateway` + `*-service` + `frontend`），并且运行手册已明确 **不再在 docker compose 中引入 legacy-community**。

但代码仓库仍保留 `legacy-community/` 模块源码与 Maven module 声明会带来长期成本：
- 持续引入不再需要的依赖面（例如 Thymeleaf/旧组件），扩大供应链与安全扫描范围
- 增加构建/IDE 索引成本与认知负担（新同学会误以为仍需维护单体入口）
- 与“微服务终局”目标冲突：SSOT（代码 + 知识库）需要对齐为“仅保留微服务体系”

本变更的目标是：在 **确认采用 A：微服务终局** 的前提下，将 `legacy-community` 从仓库主干中彻底移除，并同步清理知识库与架构文档中的遗留描述。

## Change Content
1. 从 Maven 多模块父工程中移除 `legacy-community` module
2. 删除 `legacy-community/` 源码目录
3. 更新知识库与架构文档：移除/改写所有“仅保留 legacy 源码”的当前态描述
4. 保留历史方案包（`helloagents/history/`）作为迁移期决策与对照的唯一存档来源

## Impact Scope
- **Modules:**
  - `pom.xml`（modules 列表）
  - `legacy-community/`（整个模块删除）
  - `helloagents/wiki/*`（架构/模块索引/API/数据模型等当前态文档）
  - `docs/ARCHITECTURE.md`
  - `helloagents/project.md`、`helloagents/CHANGELOG.md`、`helloagents/history/index.md`
- **Files:**
  - 见 task.md 的文件级清单
- **APIs:**
  - 不新增/不修改对外 API；仅删除文档中对 legacy 页面路由的描述
- **Data:**
  - 不做数据迁移；仅删除文档中对 legacy Redis key 的描述（代码已不存在相关读写）

## Core Scenarios

### Requirement: 删除 legacy-community（仓库主干不再包含单体模块）
**Module:** build/knowledge-base
以“微服务终局”为前提，仓库中不再出现 `legacy-community` 的源码与构建入口。

#### Scenario: 构建与文档一致性校验
在删除模块后：
- Maven root build 不再包含 legacy-community
- 知识库与架构文档不再把 legacy 作为“当前模块”
- 通过全仓库关键字扫描，除 `helloagents/history/` 的历史记录外，不再出现 `legacy-community`

## Risk Assessment
- **Risk:** 删除后无法在本仓库直接对照 legacy 源码（学习/回溯成本上升）
  - **Mitigation:** 历史对照以 `helloagents/history/` 为准；如仍需保留源码，可在 Git 层面保留 tag/分支（例如 `archive/legacy-community`），而不是继续污染主干。
- **Risk:** 文档链接失效（原先指向 legacy 模块文档/兼容期说明等）
  - **Mitigation:** 本变更同步更新 `helloagents/wiki/overview.md`、`helloagents/wiki/arch.md`、`docs/ARCHITECTURE.md` 等入口文件，确保无死链。
