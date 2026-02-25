# Task List: legacy 下线生产级收尾（100% 功能等价）

Directory: `.helloagents/archive/2026-01/202601170935_legacy_cutover_prod_parity/`

---

## 1. 功能对齐矩阵与验收门禁（Big-bang 必须）
- [√] 1.1 梳理旧单体入口清单（controller + 页面路由）并输出“功能对齐矩阵”，verify why.md#requirement-生产级交付观测告警灰度回滚备份压测密钥
  > Note: 见 `.helloagents/archive/2026-01/202601170935_legacy_cutover_prod_parity/legacy-parity-matrix.md`
- [√] 1.2 输出“验收用例矩阵”（覆盖旧单体全部功能），并标注自动化覆盖情况，verify why.md#change-content
  > Note: 见 `.helloagents/archive/2026-01/202601170935_legacy_cutover_prod_parity/acceptance-matrix.md`
- [√] 1.3 为 Big-bang 切换设计“切换步骤 + 回滚步骤 + 演练清单”，verify why.md#adr-101big-bang-切换策略一次性切走-legacy
  > Note: 见 `.helloagents/archive/2026-01/202601170935_legacy_cutover_prod_parity/cutover-runbook.md`

## 2. Auth：注册/激活/验证码（旧单体 100% 等价）
- [√] 2.1 增加注册 API：Controller 入口与参数校验，verify why.md#requirement-用户注册激活与验证码-scenario-注册-激活-登录闭环
- [√] 2.2 增加激活 API：激活码校验与状态更新，verify why.md#requirement-用户注册激活与验证码-scenario-注册-激活-登录闭环
- [√] 2.3 增加验证码 API：生成/校验与存储（Redis），verify why.md#requirement-用户注册激活与验证码-scenario-注册-激活-登录闭环
- [√] 2.4 补齐邮件激活链路：邮件模板/发送配置（可提供 test profile），verify why.md#requirement-用户注册激活与验证码-scenario-注册-激活-登录闭环

## 3. User：个人设置/主页信息对齐（含获赞与关注信息）
- [√] 3.1 扩展用户主页 API：输出获赞数、关注/粉丝数、是否已关注，verify why.md#requirement-私信与会话列表
- [√] 3.2 对齐头像设置能力：上传凭证/回写 URL 行为与旧单体一致，verify why.md#change-content

## 4. Content：帖子/评论/热帖与审核能力对齐
- [√] 4.1 对齐帖子审核接口：置顶/加精/删除（权限与审计），verify why.md#requirement-帖子评论热帖与审核能力-scenario-发帖-评论-热帖-置顶加精删除
- [√] 4.2 对齐评论能力：支持回复/分页/已读与通知事件字段完整，verify why.md#requirement-帖子评论热帖与审核能力-scenario-发帖-评论-热帖-置顶加精删除
- [√] 4.3 对齐热帖分数刷新口径：与旧单体一致并可观测，verify why.md#requirement-帖子评论热帖与审核能力-scenario-发帖-评论-热帖-置顶加精删除

## 5. Social：点赞/关注 API 行为与旧单体一致
- [√] 5.1 点赞接口兼容旧交互：支持 toggle 语义并返回 likeCount/likeStatus，verify why.md#requirement-点赞关注与通知联动-scenario-点赞关注触发通知
- [√] 5.2 关注接口对齐：follow/unfollow + followees/followers 列表分页 + hasFollowed，verify why.md#requirement-点赞关注与通知联动-scenario-点赞关注触发通知

## 6. Message：私信/通知（UI 行为等价）
- [√] 6.1 私信发送兼容旧模式：支持按用户名发送（toName），verify why.md#requirement-私信与会话列表-scenario-发送私信-会话聚合-已读标记
- [√] 6.2 会话列表聚合字段对齐：letterCount/unreadCount/targetUser，verify why.md#requirement-私信与会话列表-scenario-发送私信-会话聚合-已读标记
- [√] 6.3 通知列表与详情对齐：topic=comment/like/follow 的聚合与未读逻辑，verify why.md#requirement-点赞关注与通知联动-scenario-点赞关注触发通知

## 7. Search：ES 索引/高亮/reindex 与一致性
- [√] 7.1 搜索高亮与分页对齐旧单体体验，verify why.md#requirement-搜索es与索引一致性-scenario-发帖后可搜索命中并高亮
- [√] 7.2 reindex/重建策略：索引模板与重建脚本化，verify why.md#requirement-搜索es与索引一致性-scenario-发帖后可搜索命中并高亮
- [√] 7.3 Kafka 事件消费幂等与回放策略固化（含 schema 版本），verify why.md#impact-scope

## 8. Analytics：UV/DAU 口径与采集链路
- [√] 8.1 网关采集策略与旧单体口径对齐，verify why.md#requirement-统计uvdau与采集链路-scenario-访问采集-区间统计查询
- [√] 8.2 analytics-service 区间查询与权限边界（ADMIN/MODERATOR）对齐，verify why.md#requirement-统计uvdau与采集链路-scenario-访问采集-区间统计查询

## 9. 全依赖接入与 docker compose 生产化
- [√] 9.1 扩展 `deploy/docker-compose.yml`：加入全部微服务容器（或提供独立 compose），verify why.md#requirement-生产级交付观测告警灰度回滚备份压测密钥-scenario-docker-compose-一键拉起--全链路可观测--可回滚
- [√] 9.2 为各服务提供容器化入口（Dockerfile/启动参数/健康检查），verify why.md#requirement-生产级交付观测告警灰度回滚备份压测密钥-scenario-docker-compose-一键拉起--全链路可观测--可回滚
- [√] 9.3 Nacos 配置中心：为每个服务准备 dataId（`${spring.application.name}.yaml`）示例与初始化步骤，verify why.md#impact-scope
- [√] 9.4 Kafka topic/ES index 初始化与权限（最小化）策略落地，verify why.md#impact-scope

## 10. 可观测性与告警（指标/日志/追踪）
- [√] 10.1 指标采集：引入 Prometheus 抓取与 Grafana 仪表盘（compose 可运行），verify why.md#adr-102-观测与告警栈docker-compose
- [√] 10.2 日志聚合：引入 Loki/Promtail（或等价方案）并统一 trace 关联，verify why.md#adr-102-观测与告警栈docker-compose
- [√] 10.3 Trace 追踪：统一 trace 传播（建议兼容 W3C traceparent），并落地到 compose，verify why.md#adr-102-观测与告警栈docker-compose
- [√] 10.4 告警规则：关键 SLO（错误率/延迟/依赖不可用）告警可触发，verify why.md#adr-102-观测与告警栈docker-compose

## 11. 灰度/回滚、数据迁移与备份/恢复演练
- [√] 11.1 切换与回滚脚本化（gateway/前端入口/配置切换），verify why.md#adr-101big-bang-切换策略一次性切走-legacy
  > Note: `deploy/docker-compose.cutover.yml` + `deploy/Dockerfile.edge` + `scripts/cutover/*` 支持 edge 入口一键切换 microservices/legacy
- [√] 11.2 MySQL 备份与恢复脚本（含演练步骤），verify why.md#adr-103-数据备份与恢复策略
- [√] 11.3 Redis/ES/Kafka 的恢复与重建策略（可演练），verify why.md#adr-103-数据备份与恢复策略

## 12. 安全与风控（权限审计/限流/密钥）
- [√] 12.1 管理/审核权限边界与审计日志（网关 + 服务双层），verify why.md#risk-assessment
  > Note: gateway `AuditLogGlobalFilter` 补齐 status/costMs；服务侧新增 `common.web.AuditLogFilter`
- [√] 12.2 限流风控：登录/敏感操作/管理操作策略与可观测，verify why.md#risk-assessment
  > Note: gateway 新增 Redis 限流（多规则）+ Prometheus 指标 `gateway_rate_limit_total` 与告警规则；auth-service 登录失败计数改为 Redis
- [√] 12.3 配置/密钥管理规范：环境变量/Nacos 注入与仓库扫描门禁，verify why.md#impact-scope

## 13. 容量与压测基线
- [√] 13.1 设计压测脚本（k6/jmeter）覆盖：登录/发帖/点赞/搜索/私信，verify why.md#success-metrics
  > Note: `loadtest/k6/community-baseline.js` + `loadtest/README.md`
- [√] 13.2 定义容量基线与阈值（p95/p99/错误率/吞吐）并记录，verify why.md#success-metrics
  > Note: 阈值与记录模板已补齐到 `.helloagents/archive/2026-01/202601170935_legacy_cutover_prod_parity/why.md#success-metrics`

## 14. CI 门禁（全依赖 + 全量回归）
- [√] 14.1 在 CI 中引入 docker compose 集成链路（全依赖），verify why.md#change-content
- [√] 14.2 自动化回归覆盖旧单体用例矩阵并作为 Required checks（后续该门禁已移除），verify why.md#change-content

## 15. Security Check
- [√] 15.1 执行安全检查（输入校验、敏感信息处理、权限控制、EHRB 风险规避）
  > Note: `scripts/security-check.sh` + 报告 `.helloagents/archive/2026-01/202601170935_legacy_cutover_prod_parity/security-check.md`

## 16. Documentation Update
- [√] 16.1 同步更新知识库：`.helloagents/*`（API/arch/data/overview）并固化“可下线 legacy”运行手册
  > Note: 更新 `.helloagents/{overview,arch,api,data}.md` + 新增 `.helloagents/modules/runbooks/legacy-cutover.md`

## 17. Testing
- [√] 17.1 增补集成测试（全依赖 profile）覆盖关键场景：注册/激活/发帖/点赞/通知/搜索/统计
- [√] 17.2 增补 API 级自动化回归：覆盖旧单体主要功能（后续该回归已移除）
