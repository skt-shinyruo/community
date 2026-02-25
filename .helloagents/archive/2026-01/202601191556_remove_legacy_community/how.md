# Technical Design: 移除 legacy-community 模块（微服务终局）

## Technical Solution

### Core Technologies
- Java 17 / Maven 多模块（父工程 `pom.xml`）
- 文档：`.helloagents/*` + `docs/ARCHITECTURE.md`

### Implementation Key Points
1. **构建层收敛：** 从父工程 `pom.xml` 的 `<modules>` 移除 `legacy-community`
2. **源码层收敛：** 删除 `legacy-community/` 目录（包含代码/资源/测试）
3. **知识库收敛：**
   - 从 `.helloagents/overview.md` 模块索引移除 legacy 条目
   - 删除 `.helloagents/modules/legacy-community.md`
   - 更新 `.helloagents/arch.md`：移除“当前定位为仅保留源码”的表述，历史单体架构仅作为“历史背景”保留
   - 更新 `.helloagents/api.md`、`.helloagents/data.md`：移除 legacy 页面路由与 legacy Redis key 的“当前态”描述
   - 更新 `.helloagents/project.md`：模块列表去 legacy，缓存/安全等描述去除 legacy 专属内容
4. **对外文档收敛：** 更新 `docs/ARCHITECTURE.md`，移除 legacy-community 的组件说明
5. **一致性校验：** 全仓库扫描 `legacy-community`，确保仅在 `.helloagents/archive/` 中保留历史记录

## Architecture Decision ADR

### ADR-009: 从仓库主干移除 legacy-community 源码（微服务终局）
**Context:** 当前运行形态已完全以微服务 + SPA 为准，legacy-community 不参与 compose 运行；继续保留会增加维护成本与依赖暴露面，并造成架构 SSOT 不一致。  
**Decision:** 从 Maven modules 与仓库目录中删除 `legacy-community/`，知识库当前态不再包含 legacy；历史信息仅在 `.helloagents/archive/` 保留。  
**Rationale:** 降低复杂度与依赖面，使代码与文档都能明确表达“微服务终局”。  
**Alternatives:**
- 保留 legacy-community 作为参考源码 → 拒绝原因：主干长期污染，持续扩大依赖/扫描范围
- 把 legacy-community 迁到单独仓库/子模块 → 备选：若仍有教学/回溯需求，可后续单独归档；本次以“主干删除”优先
**Impact:** Maven 构建更轻量；知识库去除 legacy 入口；历史迁移方案仍可追溯但不再有源码对照。

## Security and Performance
- **Security:** 缩小依赖面与历史代码暴露面；减少安全扫描误报与旧依赖风险
- **Performance:** Maven 构建与 IDE 索引成本降低（移除一个大型模块）

## Testing and Deployment
- **Testing:** `mvn test`（root）+ 关键字扫描（`rg legacy-community`）确保无残留引用（除 history）
- **Deployment:** compose 不受影响（本来不包含 legacy）；仅需确保文档与知识库入口无死链
