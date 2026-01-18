# Task List: docker compose 最小端口暴露（避免本机端口冲突）

Directory: `helloagents/history/2026-01/202601172216_compose_minimal_ports/`

---

## 1. Compose 端口暴露最小化
- [√] 1.1 移除内部依赖组件（MySQL/Redis/Kafka/ES/Nacos/观测栈等）对宿主机的端口映射，仅保留必要对外端口（默认仅 gateway）
- [√] 1.2 移除内部微服务对宿主机端口映射（仅保留 gateway），确保 UI/API 通过统一入口访问

## 2. 回归脚本适配
- [√] 2.1 健康检查不再依赖宿主机直连各微服务端口，改为基于容器 Health Status 探活（该能力后续由其他验证方式承载）

## 3. 文档同步
- [√] 3.1 更新运行手册：启动前端口要求调整为“仅需 gateway 端口”，并补充可选端口映射覆盖文件用于调试

## 4. 验证
- [√] 4.1 `docker compose config -q` 校验通过
- [√] 4.2 `docker compose build`（关键服务 build）验证通过
