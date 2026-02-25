# Technical Design: 测试分层 Quick Win（减少 @SpringBootTest 占比）

## Technical Solution

### Core Technologies
- Spring Boot Test（来自 `spring-boot-starter-test`）
- JUnit Jupiter（JUnit 5）
- Mockito（单元测试 mock/stub）
- Spring MVC Slice Test：`@WebMvcTest` + `MockMvc`
- （已有但谨慎使用）Testcontainers：仅用于确有必要的集成级用例

### Implementation Key Points
1. **Controller 测试切片化（优先级最高）**
   - 将 `@SpringBootTest + @AutoConfigureMockMvc` 的 Controller 测试迁移为 `@WebMvcTest(controllers = ...)`
   - 对下游依赖使用 `@MockBean` 注入 mock，避免触发 DB/Kafka/Nacos 等外部依赖
   - 对安全相关用例：
     - 优先在 `@WebMvcTest` 中显式导入/启用与生产一致的安全配置（如需要）
     - 使用 `@WithMockUser` / request header 模拟鉴权上下文（按当前工程约定）
2. **Service / Consumer / Outbox 测试单元化（高收益）**
   - 去掉 `@SpringBootTest`，改用 `@ExtendWith(MockitoExtension.class)` + 构造注入/`@InjectMocks`
   - 测试重点放在：分支覆盖、异常映射、幂等处理、参数边界、与依赖的交互契约
3. **集成测试保留与瘦身（不改变构建流程）**
   - 保留确有价值的 `@SpringBootTest` 用例（wiring/事务/序列化组合）
   - 对明显是集成测试的用例（例如 `*IntegrationTest`）保持名称与语义一致；在实现阶段评估是否可拆分为“单元（逻辑）+ 集成（极少数兜底）”
4. **测试配置最小化**
   - 依赖 `src/test/resources/application.yml` 作为测试配置入口，确保：
     - 默认不连接外部 Nacos/真实 Kafka/真实 DB
     - 使用内存 DB/H2 或 mock 替代（以当前各服务测试资源文件为基线）

## Security and Performance
- **Security：**
  - 测试中禁止引入真实密钥/Token；使用固定的测试值或 mock
  - 对 internal/ops 接口的安全用例，优先保持可重复与无外部依赖
- **Performance：**
  - 通过减少 `@SpringBootTest` 用例数量降低启动成本
  - 成功标准建议以“`mvn test` 总耗时/波动降低（可测）”为导向，但本方案不强制引入新的构建插件或分层执行策略

## Testing and Deployment
- **Testing：**
  - 单元/切片测试：默认随 `mvn test` 执行
  - 集成测试：数量尽量少且稳定；不引入新的执行 profile/插件（Quick win 约束）
  - 验收建议：
    - `@SpringBootTest` 测试文件数量显著下降（优先覆盖 `user-service/content-service/message-service` 中的 Controller 用例）
    - 关键接口/安全用例在切片测试中仍有覆盖
    - CI 与本地执行稳定性提升（至少可重复执行多次不出现偶发失败）
- **Deployment：** 无部署变更

