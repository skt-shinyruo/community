# Nacos Config 示例（本地/开发）

本目录提供各服务的 `Data ID = ${spring.application.name}.yaml` 的示例内容，便于在本地/测试环境快速初始化 Nacos 配置中心。

## 使用步骤（docker compose）

1. 启动基础设施（见 `deploy/docker-compose.yml`），确保 Nacos 已可访问：
   - 控制台：`http://localhost:8848/nacos`
2. 登录 Nacos（默认账号一般为 `nacos/nacos`；如镜像版本不同请以实际为准）。
3. 进入 **配置管理 → 配置列表 → 新建配置**：
   - Data ID：例如 `auth-service.yaml`
   - Group：`DEFAULT_GROUP`（或与你的 `NACOS_GROUP` 保持一致）
   - 配置格式：YAML
4. 将对应文件内容粘贴保存。

## 安全提示

- 示例中使用了 `${...}` 形式的占位符，依赖运行时环境变量注入；不要把真实生产密钥提交到仓库。
- 生产建议：使用不同 namespace/group/profile 做环境隔离，并启用仓库密钥扫描门禁。

