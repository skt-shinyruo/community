# Task List: 异常处理语义收敛与分域错误码体系

Directory: `.helloagents/archive/2026-02/202602061145_exception_handling_convergence/`

---

## 1. common（异常基座与通用映射）
- [√] 1.1 收敛 `GlobalExceptionHandler`：补齐常见 MVC 异常映射并清零 `catch(Exception)`，验证 why.md#requirement-统一错误协议http-status--result
- [√] 1.2 收敛 `BusinessException/ErrorCode` 使用规范：新增领域错误码落地约定与代码段划分说明（必要时更新对应 wiki），验证 why.md#requirement-分域错误码体系
- [√] 1.3 收敛 Kafka/事件工具类：清零 `EventEnvelopeParser/KafkaTraceSupport` 的泛化 Exception（含函数式接口与 ignore catch），验证 why.md#requirement-生产代码清零消除泛化-exception

## 2. gateway（WebFlux 错误收敛与协议一致）
- [√] 2.1 新增/完善 gateway 全局异常收敛（非 401/403）：将 WebFlux 常见异常映射为 `Result` + 4xx/5xx，并回填 traceId，验证 why.md#requirement-统一错误协议http-status--result
- [√] 2.2 清零 gateway 生产代码中的 `catch(Exception)`（例如序列化失败分支），验证 why.md#requirement-生产代码清零消除泛化-exception

## 3. 分域错误码（按服务领域拆分）
- [√] 3.1 auth：完善/补齐鉴权域错误码使用点（必要时新增更细分 code），并在业务代码中替换泛化 Exception，验证 why.md#requirement-分域错误码体系
- [√] 3.2 user：新增 user 领域错误码并替换业务代码中的泛化 Exception，验证 why.md#requirement-分域错误码体系
- [√] 3.3 content：新增 content 领域错误码并替换业务代码中的泛化 Exception，验证 why.md#requirement-分域错误码体系
- [√] 3.4 social：新增 social 领域错误码并替换业务代码中的泛化 Exception，验证 why.md#requirement-分域错误码体系
- [√] 3.5 message：新增 message 领域错误码并替换业务代码中的泛化 Exception，验证 why.md#requirement-分域错误码体系
- [√] 3.6 search：新增 search 领域错误码并替换业务代码中的泛化 Exception，验证 why.md#requirement-分域错误码体系
- [√] 3.7 analytics：新增 analytics 领域错误码并替换业务代码中的泛化 Exception，验证 why.md#requirement-分域错误码体系

## 4. 生产代码清零（全仓库 `src/main` 范围）
- [√] 4.1 全仓库扫描并逐模块清零 `catch(Exception)`（允许框架配置类按约定豁免），验证 why.md#requirement-生产代码清零消除泛化-exception
- [√] 4.2 全仓库扫描并逐模块替换 `throws Exception` 为更具体异常或边界转换（允许框架配置类按约定豁免），验证 why.md#requirement-生产代码清零消除泛化-exception
- [√] 4.3 为“清零目标”增加可执行校验（脚本/JUnit 门禁二选一），避免回潮，验证 why.md#requirement-生产代码清零消除泛化-exception

## 5. Testing（集成/契约测试）
- [√] 5.1 gateway 契约测试：401/403/429/400/500 响应的 HTTP status + `Result` 字段（含 traceId），验证 why.md#requirement-关键异常路径集成契约测试
- [√] 5.2 auth-service 契约测试：登录失败/未认证/参数非法的 status + `Result.code`，验证 why.md#requirement-关键异常路径集成契约测试
- [√] 5.3 content-service 契约测试：未登录写接口/资源不存在/参数非法的 status + `Result.code`，验证 why.md#requirement-关键异常路径集成契约测试
- [√] 5.4 其他服务补齐最低覆盖：每个服务至少 1 条关键异常路径契约测试，验证 why.md#requirement-关键异常路径集成契约测试

## 6. Security Check
- [√] 6.1 执行安全检查（输入校验、敏感信息回显、权限控制、internal/ops 风险回归），确保异常收敛不引入绕过与信息泄露

## 7. Documentation Update（知识库同步）
- [√] 7.1 更新 `.helloagents/modules/common.md` 与 `.helloagents/api.md`：补齐错误协议与分域错误码约定
- [√] 7.2 如 gateway 新增全局错误收敛：更新 `.helloagents/modules/gateway.md`
- [√] 7.3 更新 `.helloagents/CHANGELOG.md` 并将已执行方案包迁移到 `.helloagents/archive/YYYY-MM/`（执行阶段强制）
