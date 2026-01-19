# Task List: 移除 legacy-community 模块（微服务终局）

Directory: `helloagents/history/2026-01/202601191556_remove_legacy_community/`

---

## 1. Build / Codebase 收敛
- [√] 1.1 从父工程 `pom.xml` 移除 `<module>legacy-community</module>`，确保 Maven reactor 不再包含 legacy-community
- [√] 1.2 删除目录 `legacy-community/`（源码/资源/测试/模块 pom）

## 2. Knowledge Base（当前态文档收敛）
- [√] 2.1 更新 `helloagents/wiki/overview.md`：移除 legacy 模块条目与相关描述/链接
- [√] 2.2 更新 `helloagents/wiki/arch.md`：移除 legacy 的“当前定位”描述，历史单体仅保留为背景
- [√] 2.3 删除 `helloagents/wiki/modules/legacy-community.md` 与 `helloagents/wiki/modules/auth.md`，并清理引用
- [√] 2.4 更新 `helloagents/wiki/api.md`：移除 legacy 页面路由与兼容期说明（只保留 `/api/**`）
- [√] 2.5 更新 `helloagents/wiki/data.md`：移除 legacy Redis key（ticket/kaptcha）与 legacy 写入口说明
- [√] 2.6 更新 `helloagents/project.md`：模块列表与缓存/安全说明去 legacy

## 3. 外部文档同步
- [√] 3.1 更新 `docs/ARCHITECTURE.md`：移除 legacy-community 的组件说明

## 4. Security Check
- [√] 4.1 执行 `scripts/security-check.sh`（确保无敏感信息/危险脚本/依赖问题暴露），记录结果

## 5. Testing / Verification
- [√] 5.1 执行 `mvn test`（root），确保构建通过
- [√] 5.2 全仓库扫描 `legacy-community`：除 `helloagents/history/` 外无残留引用

## 6. 收尾（知识库与方案包生命周期）
- [√] 6.1 更新 `helloagents/CHANGELOG.md`（记录移除历史单体源码的变更）
- [√] 6.2 迁移方案包到 `helloagents/history/2026-01/202601191556_remove_legacy_community/` 并更新 `helloagents/history/index.md`

---

## Notes
> 本变更以“微服务终局”为前提：历史单体信息仅在 `helloagents/history/` 中保留，主干不再包含单体源码与构建入口。
