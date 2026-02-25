# Task List: testcontainers_auth_tests

Directory: `.helloagents/archive/2026-01/202601181638_testcontainers_auth_tests/`

---

## 1. auth-service（测试分层：集成测试/切片测试）
- [√] 1.1 为 `auth-service` 增加 Testcontainers 依赖（JUnit Jupiter），用于集成测试启动 Redis 容器
- [√] 1.2 改造 `AuthControllerTest`：作为集成测试使用 Testcontainers Redis，并通过 `@DynamicPropertySource` 注入 `spring.data.redis.*`
- [√] 1.3 新增 `AuthControllerWebMvcTest`：`@WebMvcTest(AuthController)` + `@MockBean`，验证 Controller 行为不依赖外部服务

## 2. Testing
- [√] 2.1 执行 `mvn -pl auth-service -am test`，确保 `AuthControllerTest` 不再因 Redis 连接失败而返回 500
- [√] 2.2 执行 `mvn -q test`（全仓）确保 CI backend-test 通过

## 3. Documentation Update
- [√] 3.1 更新 `.helloagents/project.md` 的测试分层约定：集成测试用 Testcontainers，单元/切片测试用 mock
- [√] 3.2 更新 `.helloagents/CHANGELOG.md`（Unreleased）记录本次修复点
