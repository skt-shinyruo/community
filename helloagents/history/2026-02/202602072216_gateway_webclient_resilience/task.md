# 任务清单：网关 WebClient 全局超时与连接池兜底

Directory: `helloagents/plan/202602072216_gateway_webclient_resilience/`

---

## 1. gateway
- [√] 1.1 新增 `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewayWebClientProperties.java`：定义 `gateway.webclient.*`（超时 + 连接池）配置项并提供默认值；核对 why.md 中“gateway-webclient-global-resilience”
- [√] 1.2 更新 `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewayWebClientConfig.java`：构建 `ConnectionProvider` + `HttpClient`（connect/response/read/write timeout）并注入 `@LoadBalanced WebClient.Builder`；依赖任务 1.1
- [√] 1.3 更新 `gateway/src/main/resources/application.yml` 与 `deploy/nacos-config/gateway.yaml`：补齐 `gateway.webclient.*` 示例/推荐值/注释；依赖任务 1.1

## 2. Analytics Capacity Guard（不改变策略，仅确保协同）
- [√] 2.1 复核 `gateway/src/main/java/com/nowcoder/community/gateway/analytics/AnalyticsCollectDispatcher.java`：确保其“有界队列 + 并发上限 + timeout”仍为主链路隔离策略，并确认全局兜底配置不会破坏其可丢弃语义

## 3. Security Check
- [√] 3.1 执行安全检查（连接池上限、pending acquire 限制、超时默认值合理性；确认不会引入敏感信息泄漏）

## 4. Documentation Update
- [√] 4.1 更新 `docs/DEPLOYMENT.md` 与 `helloagents/wiki/modules/gateway.md`：补充 `gateway.webclient.*` 的含义、默认值、调参建议与观测指标

## 5. Testing
- [√] 5.1 新增网关测试：`gateway/src/test/java/.../GatewayWebClientConfigTest.java`（或等价测试文件），验证配置绑定与超时行为（延迟响应/无响应场景应在 response timeout 内失败）；依赖任务 1.2
