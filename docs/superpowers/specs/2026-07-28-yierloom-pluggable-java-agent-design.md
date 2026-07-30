# YierLoom 可插拔 Java Agent 设计

## 背景

`backend/runtime-diagnostics-agent` 当前是一个单模块 Java Agent。入口类直接创建所有 Probe，直接拼装 Byte Buddy matcher 和 Advice，并由一个固定的 `DiagnosticsConfig` record 保存所有探针配置。增加增强能力需要修改 Agent、重新打包并重新发布完整 Agent JAR。

YierLoom 将这个模块重构为一个可扩展的 Java Agent 平台。内置诊断能力与外部插件使用同一套 SPI；添加可信的内部插件时，只需把插件 fat JAR 放入配置目录并重启目标 JVM，不需要重新构建 Agent。

设计参考了 OpenTelemetry Java Agent、Elastic APM Agent、SkyWalking、Pinpoint 和 Glowroot 的插件模型。成熟 Agent 的共同点是由核心控制 Transformer、共享一套 Byte Buddy 运行时，并在受控 ClassLoader 边界内加载扩展。YierLoom 保留这些边界，但首版不引入 PF4J、插件市场或热更新。

## 目标

1. 最终只使用一个 `yierloom-agent.jar` 作为 `-javaagent` 入口。
2. 外部插件从配置目录加载，每个插件具有独立依赖空间。
3. 插件可以提供定时或运行时任务、Byte Buddy 字节码增强，或同时提供两者。
4. 插件可以通过统一事件出口产生诊断事件，不在业务线程执行阻塞输出。
5. Advice 可以通过受管 Observation 通道把观测数据交给同一插件的 Runtime capability，用于采样、聚合和汇总。
6. 核心统一管理配置、生命周期、调度器、事件队列、Transformer 和故障隔离。
7. 当前 `method`、`exception`、`http`、`jdbc`、`redis`、`kafka`、`thread` 和 `jvm` 探针全部迁移为内置插件。
8. Agent 或单个插件失败时，默认不阻止宿主应用启动和运行。
9. 提供测试工具，使插件作者可以在部署前验证打包、SPI、Advice 和 Helper 契约。

## 非目标

首版不支持：

- 插件热加载、卸载或动态更新。
- 运行时 attach；只支持 JVM 启动时的 `premain`。
- 插件之间的依赖、服务发现或直接调用。
- 插件市场、远程下载、签名校验或版本自动解析。
- 不可信插件的权限沙箱。
- 外部插件直接访问原始 JVM `Instrumentation`。
- YAML 配置或动态配置中心。
- 插件提供自定义事件 exporter；首版由核心提供 JSON Lines 输出。

插件是可信内部代码。ClassLoader 隔离解决依赖冲突和类型边界，不构成安全沙箱。

## 架构决策

采用方案 A：受管式 SPI、共享 Byte Buddy SDK、每个外部插件一个独立 ClassLoader、核心集中管理 Transformer。

未采用的方案：

| 方案 | 未采用原因 |
| --- | --- |
| 插件直接获得 `Instrumentation` | API 最小，但插件可以绕过排序、错误监听、重置和可见性策略，无法形成稳定治理边界。 |
| 所有插件共用一个 ClassLoader | 实现简单，但 fat JAR 中的依赖版本会互相覆盖；Pinpoint 的共享插件加载经验也说明这会形成长期冲突。 |
| PF4J | 能处理通用应用插件生命周期，但不能替代 Java Agent 的 bootstrap bridge、Helper 注入和 Transformer 管理，引入后仍需自建关键部分。 |
| 每个插件自带并隔离 Byte Buddy | 版本独立，但 matcher、Transformer 和核心之间会产生类型身份问题，也会显著增加内存和包体积。 |

## 模块结构

现有模块迁移为一个 Maven 聚合模块：

```text
backend/yierloom/
  pom.xml
  yierloom-plugin-api/
  yierloom-bytebuddy-sdk/
  yierloom-agent-core/
  yierloom-plugin-testkit/
  yierloom-agent/
```

模块职责：

| 模块 | 职责 | 依赖约束 |
| --- | --- | --- |
| `yierloom-plugin-api` | 插件根接口、描述符、运行时能力、配置、调度、Observation、事件和 bootstrap bridge | 只依赖 JDK |
| `yierloom-bytebuddy-sdk` | instrumentation capability、matcher/Advice/Transformer 贡献契约和 Helper 声明 | 依赖 Plugin API 和共享 Byte Buddy |
| `yierloom-agent-core` | 配置加载、插件发现、ClassLoader、生命周期、事件管线、Transformer 管理和内置插件 | 依赖 API、SDK 和 Byte Buddy |
| `yierloom-plugin-testkit` | 插件契约验证、隔离加载测试和 forked-JVM 测试支持 | 测试范围依赖 API、SDK 和 Byte Buddy |
| `yierloom-agent` | JDK-only `premain` bootstrap、嵌套 JAR 打包和单一发布产物 | 启动前不得静态引用尚未装载的 Core 类型 |

生产代码包名：

```text
com.nowcoder.yierloom.api
com.nowcoder.yierloom.sdk
com.nowcoder.yierloom.core
com.nowcoder.yierloom.bootstrap
com.nowcoder.yierloom.plugins
```

`com.nowcoder.yierloom.plugins` 保留给内置插件。Core 与 Bootstrap 的内部类型不属于插件兼容契约。

## 发布产物与 ClassLoader

### 单一 Agent 产物

`yierloom-agent.jar` 是唯一部署产物，Manifest 至少声明：

```text
Premain-Class: com.nowcoder.yierloom.bootstrap.YierLoomAgent
Can-Redefine-Classes: false
Can-Retransform-Classes: false
```

外层 JAR 只直接暴露 JDK-only bootstrap 代码，并以嵌套 JAR 形式携带 Plugin API、SDK、Core、内置插件和一份未重定位的 Byte Buddy 运行时。当前将 Byte Buddy relocation 到 Agent 私有包的做法会被移除，因为外部插件必须与 Core 共享相同的 Byte Buddy 类型身份。

Bootstrap 使用安全的临时目录提取嵌套 JAR。Plugin API JAR 先通过 `Instrumentation.appendToBootstrapClassLoaderSearch` 加入 Bootstrap ClassLoader，之后才加载 Core。Core、SDK、Byte Buddy 和内置插件由 `YierLoomEngineClassLoader` child-first 加载。关闭时先关闭相关 ClassLoader，再清理临时文件；无法立即删除的文件注册为 JVM 退出时删除。

### ClassLoader 拓扑

```text
Bootstrap ClassLoader
  `-- yierloom-plugin-api + YierLoomBridge

System ClassLoader
  `-- YierLoomAgent bootstrap
       `-- YierLoomEngineClassLoader
            |-- yierloom-agent-core
            |-- yierloom-bytebuddy-sdk
            |-- one Byte Buddy runtime
            |-- built-in plugins
            |-- YierLoomPluginClassLoader(plugin-a.jar)
            `-- YierLoomPluginClassLoader(plugin-b.jar)
```

每个外部插件 JAR 对应一个可关闭的 `YierLoomPluginClassLoader`。加载规则为：

1. JDK 类、`com.nowcoder.yierloom.api`、`com.nowcoder.yierloom.sdk` 和 `net.bytebuddy` 始终 parent-first。
2. 插件自己的类和私有依赖 child-first。
3. Plugin ClassLoader 拒绝外部插件对 `com.nowcoder.yierloom.core` 和 `com.nowcoder.yierloom.bootstrap` 的正常类解析，Testkit 同时禁止插件对这些包建立静态依赖。
4. 一个外部 JAR 必须且只能在自身的 `META-INF/services` 中声明一个 `YierLoomPlugin` Service Provider；发现时不把父 ClassLoader 中的 Provider 计入该 JAR。
5. 外部插件依赖打入各自 fat JAR，但不得打入 Plugin API、SDK 或 Byte Buddy。

内置插件由 Engine ClassLoader 加载，可以在同一 JAR 中声明多个 Service Provider；它们仍通过公开 SPI 被发现和启动，不使用另一套生命周期入口。

## 插件 SPI

### 根接口与运行时能力

JDK-only 接口位于 `yierloom-plugin-api`：

```java
package com.nowcoder.yierloom.api;

public interface YierLoomPlugin {
    PluginDescriptor descriptor();
}

public interface RuntimeCapability {
    void start(PluginRuntimeContext context) throws Exception;

    void stop() throws Exception;
}
```

`ServiceLoader` 只发现 `YierLoomPlugin`。插件按需实现 `RuntimeCapability`，Core 使用能力接口判断要执行的生命周期。

插件构造器必须无副作用。资源创建、配置缓存和任务注册发生在 `start()`；`stop()` 必须幂等，并允许在 `start()` 部分失败后被调用。后台任务必须使用 Core 提供的 `ManagedScheduler`，不得自行创建无法治理的线程池。

### Instrumentation 能力

Byte Buddy 相关接口位于 `yierloom-bytebuddy-sdk`，不会被加入 Bootstrap ClassLoader：

```java
package com.nowcoder.yierloom.sdk;

public interface InstrumentationCapability {
    List<InstrumentationModule> instrumentations(PluginConfig config);
}
```

同一个插件可以只实现 `RuntimeCapability`、只实现 `InstrumentationCapability`，或同时实现两者。纯运行时插件因此不需要依赖 Byte Buddy SDK。

Provider 必须至少提供一种有效能力。只实现根接口的 Provider 被拒绝；Instrumentation-only Provider 必须声明至少一个 module。组合插件可以根据配置不声明 instrumentation module，但仍必须具有可启动的 Runtime capability。

`InstrumentationModule` 表示插件内一个独立的增强单元，至少声明本地唯一 module ID、有序的 `TypeInstrumentation` 列表和 Helper 类集合。`TypeInstrumentation` 提供：

- 类型 matcher。
- 可选 ClassLoader matcher。
- Advice 便捷注册或标准 Byte Buddy `AgentBuilder.Transformer`。
- 转换后字节码在运行期需要访问的 Helper 类名。

插件可以贡献 matcher、Advice 和 Transformer，但不能接收 `Instrumentation`，也不能自行调用 `installOn()` 或 `addTransformer()`。Core 为每个 instrumentation module 建立并持有独立的 Transformer 句柄，按插件 `order`、插件 ID、module 声明顺序确定安装顺序。

### PluginDescriptor

`PluginDescriptor` 包含：

| 字段 | 规则 |
| --- | --- |
| `id` | 全局稳定且唯一，匹配 `[a-z][a-z0-9-]*` |
| `name` | 面向人的非空名称 |
| `version` | 插件自身的语义版本 |
| `apiVersion` | 插件编译时要求的 YierLoom API/SDK 版本 |
| `defaultEnabled` | 未显式配置时是否启用 |
| `order` | 较小值先启动；相同值按 ID 排序 |

API/SDK 同版本发布。兼容规则为 major 必须相同，Agent 的 minor 必须大于或等于插件声明的 minor；patch 不参与能力判断。首版不接受版本范围表达式。

内置插件 ID 是保留 ID。外部插件不能覆盖内置插件；多个外部 JAR 使用同一 ID 时，所有冲突 JAR 都被拒绝，而不是由文件遍历顺序选择一个。

## PluginRuntimeContext

`PluginRuntimeContext` 只暴露稳定的 Plugin API 和 JDK 类型：

```java
public interface PluginRuntimeContext {
    PluginConfig config();

    ManagedScheduler scheduler();

    ObservationChannel observations();

    EventSink events();

    System.Logger logger();

    Clock clock();
}
```

每个 Context 绑定一个插件 ID。插件不能通过 Context 获得 Core、ClassLoader、其他插件、应用 Spring Context 或原始 `Instrumentation`。

`ManagedScheduler` 给每个插件提供独立的任务所有权和取消句柄。任务成功一次会清零连续失败计数；同一任务连续失败三次时自动取消，并产生一次限频后的 Agent 状态事件。插件停止或启动回滚时，Core 会取消该插件的全部遗留任务。

`ObservationChannel.register(ObservationHandler)` 允许 Runtime capability 在 `start()` 中注册一个同插件 Handler。第二次注册被拒绝，插件停止、启动回滚或 Handler 连续失败三次时由 Core 自动解除。Handler 只接收相同插件 ID 的 `PluginObservation`，不能订阅其他插件。

`PluginObservation` 是 Advice 到 Runtime 的内部消息，包含 observation type、不可变 String attributes，以及 boolean、long、double 三类不可变数值字段。Advice 使用 `YierLoomBridge.observe(pluginId, observation)` 非阻塞入队；Core consumer 线程随后调用对应 Handler。Handler 不运行在业务线程，不得执行阻塞 I/O；它用于采样、计数和聚合，最终可通过 `EventSink` 产生诊断事件。

`EventSink.emit(DiagnosticEvent)` 是非阻塞调用，并用 boolean 返回事件是否进入队列。`DiagnosticEvent` 包含 action、可选时间戳、不可变 String attributes，以及 boolean、long、double 三类不可变数值字段。EventSink 自动补充插件 ID、`event.category=yierloom` 和缺失的时间戳，不允许把插件私有对象传入 Core。

## 配置

### 来源与优先级

配置从低到高覆盖：

```text
Descriptor / 插件代码默认值
  -> yierloom.properties
  -> 环境变量
  -> JVM System Properties
  -> -javaagent 参数
```

全局及插件配置使用统一命名：

```properties
yierloom.enabled=true
yierloom.plugins.dir=/opt/yierloom/plugins
yierloom.events.queue-capacity=8192
yierloom.plugins.http.enabled=true
yierloom.plugins.http.capture-headers=false
yierloom.plugins.http.slow-threshold=2s
```

`yierloom.enabled` 默认 `false`。可选配置文件路径通过 `yierloom.config` 指定。`-javaagent` 参数是逗号分隔的 `key=value`，同时接受 bootstrap 简写 `config` 和 `plugins-dir`；包含复杂列表或逗号的值应写入 Properties 文件。

`yierloom.plugins.dir` 默认不设置；未设置时只加载内置插件。显式目录相对路径按 JVM `user.dir` 解析。显式目录不存在、不可读或不是目录时记录启动错误并跳过全部外部插件，内置插件继续启动。目录中只有扩展名为 `.jar` 的普通文件参与发现，候选文件先按绝对路径排序，最终插件启动顺序仍只由 Descriptor 决定。

环境变量的全局键使用 `YIERLOOM_` 前缀，例如 `YIERLOOM_ENABLED` 和 `YIERLOOM_PLUGINS_DIR`。插件环境变量使用可逆边界：`YIERLOOM_PLUGIN__HTTP__SLOW_THRESHOLD` 对应 `yierloom.plugins.http.slow-threshold`；双下划线分隔插件 ID 与配置键，单下划线表示 kebab-case 中的连字符。

### PluginConfig

每个插件只看到 `yierloom.plugins.<id>.*` 下的不可变配置视图。`PluginConfig` 提供 String、boolean、整数、long、double、`Duration` 和 String list 的可选值、必填值及带默认值读取。

只有键缺失时才使用默认值。用户显式提供的值类型错误或越过插件校验范围时抛出 `PluginConfigurationException`，Core 拒绝对应插件，不能像当前配置加载器一样静默回退。错误日志显示来源和键名，但不输出配置值。Core 的未知全局键产生告警；插件私有键的合法性由插件在 `instrumentations()` 或 `start()` 阶段验证。

插件最终启用条件为：Agent 全局启用，并且 `yierloom.plugins.<id>.enabled` 的显式值或 Descriptor 默认值为 `true`。

## 启动与生命周期

### 启动流程

```text
JVM premain
  -> JDK-only YierLoomAgent bootstrap
  -> 提取并向 Bootstrap ClassLoader 注册 Plugin API
  -> 创建 YierLoomEngineClassLoader
  -> 加载完整配置
  -> 初始化承载 Observation 与最终事件的有界管线并一次性绑定 YierLoomBridge
  -> 发现内置 Provider 和每个外部 JAR 的 Provider
  -> 校验 JAR、Descriptor、API 版本、ID 和配置
  -> 收集并校验 instrumentation declarations
  -> 创建插件 Context 并启动 RuntimeCapability
  -> 为成功启动的插件统一安装 instrumentation modules
  -> 输出启动摘要并进入运行状态
```

对于同时具有 Runtime 和 Instrumentation 能力的插件，Runtime 必须先成功启动，使 Advice 产生事件前采集组件已经就绪。Runtime 启动失败时，该插件的所有 instrumentation module 都不安装。纯 Instrumentation 插件通过声明校验后直接进入安装阶段。

插件按 `order`、ID 启动。`order` 只提供确定性，不代表插件依赖。插件不能通过启动顺序假设另一个插件存在。

### 状态

```text
DISCOVERED -> VALIDATED -> STARTING -> ACTIVE -> STOPPING -> STOPPED
                              |
                              `-> FAILED
```

描述符、兼容性或配置校验失败的插件从 `VALIDATED` 前进入 `FAILED`。Runtime 启动或初始 Transformer 安装失败时，Core 按逆序移除该插件已安装的 Transformer、调用 `stop()` 并取消托管任务，然后标记 `FAILED`。

首版不支持运行时重新发现。插件目录内容和 Provider 集合在 `premain` 期间形成快照，生命周期与宿主 JVM 一致。

### 关闭流程

Core 只注册一个 shutdown hook：

1. 引擎切换到 `STOPPING`，调度器拒绝新任务，EventSink 暂时继续接受插件的最终事件。
2. 停止接受新 Observation，并解除所有插件的 Observation Handler。
3. 按启动顺序的逆序调用插件 `stop()`。
4. 强制取消各插件遗留的托管任务。
5. 移除 Core 持有的 Transformer。
6. 清空 `YierLoomBridge` 的 Core endpoint，使已增强但仍在执行的业务代码转为 no-op。
7. 在固定的 2 秒上限内排空最终事件，然后停止 exporter；超时后丢弃剩余事件并记录 dropped counter。
8. 关闭外部插件和 Engine ClassLoader，清理临时文件。

关闭是 best-effort；一个插件的 `stop()` 异常不会阻止其他插件清理。由于不支持运行时卸载，关闭时不尝试重新转换已经增强的业务类，JVM 退出会回收这些类。

## Advice、Helper 与 Bridge

Engine ClassLoader 是 System ClassLoader 的子加载器，宿主类不能直接引用 Core 或插件类。增强代码按两条受管路径传递数据：

```text
instrumented application method
  -> inlined Advice / injected Helper
  -> bootstrap-visible YierLoomBridge.observe
  -> bounded Core pipeline
  -> same-plugin ObservationHandler
  -> plugin aggregation
  -> EventSink
  -> bounded Core pipeline
  -> JSON Lines exporter

instrumented application method
  -> YierLoomBridge.emit
  -> bounded Core pipeline
  -> JSON Lines exporter
```

`YierLoomBridge` 位于 Plugin API。Core 在实例化外部 Provider 前一次性绑定 endpoint；重复绑定被拒绝，关闭时只有当前 endpoint 可以通过 compare-and-clear 解除自身绑定。Bridge 本身只依赖 JDK 和 Plugin API，在边界内抑制普通插件异常。Advice 使用已注册的插件 ID 调用 `observe()` 或 `emit()`：未知或未启用的插件 ID 始终被丢弃，`observe()` 在没有对应 Handler 时也被丢弃，`emit()` 不要求插件注册 Handler。关闭时清除 endpoint，避免静态引用阻止 Core ClassLoader 回收。

Byte Buddy Advice 类是构建转换的模板；标注的 enter/exit 方法内联到目标方法。Advice 中任何不能被内联、且会留在转换后字节码中的类引用，都必须作为 Helper 声明。Core 在转换目标类之前，把声明的 Helper 依赖闭包注入目标 ClassLoader；bootstrap 目标使用 Instrumentation 支持的 bootstrap 注入路径，自定义应用 ClassLoader 使用对应的 ClassInjector。

Helper 必须满足：

1. 只依赖 JDK、Plugin API 和同一 module 声明的其他 Helper。
2. 二进制类名在所有启用 module 中唯一。
3. 不引用插件实例、Core、SDK 或 Byte Buddy 运行时。
4. 不执行阻塞 I/O。

Advice 的 `@Advice.OnMethodEnter` 和 `@Advice.OnMethodExit` 必须设置 `suppress = Throwable.class`。自定义 Transformer 生成的字节码同样必须保证增强逻辑异常不会传播到业务方法。Testkit 对 Advice 注解、Helper 闭包和禁止依赖执行静态检查。

## 事件管线

运行时插件通过 Context 中的 scoped `EventSink` 发出最终事件；Advice 通过 Bridge 发出内部 Observation 或最终事件。三条路径进入同一个有界、非阻塞队列。队列中的内部消息携带 Core 分配的插件身份，不能由一个插件路由到另一个插件。

队列容量由 `yierloom.events.queue-capacity` 配置，默认 8192。业务线程和插件线程只尝试入队；队列满时立即丢弃并累加按插件及消息类型划分的 dropped counter，不等待、不回退为同步处理。Core 的单一 daemon consumer 把 Observation 交给同插件 Handler，把最终事件序列化为 JSON Lines 并写入 `stdout`。Handler 发出的最终事件重新进入队列，不在 consumer 调用栈中递归导出。

事件中的 `service.name` 依次读取 `yierloom.service.name`、`otel.service.name`、`OTEL_SERVICE_NAME` 和 `SERVICE_NAME`，均未设置时使用 `unknown`。这一顺序只替换旧 Agent 自有前缀，保留现有 OpenTelemetry 与通用环境变量回退语义。

首版保留现有事件 action、采样、限流、聚合和敏感信息处理语义，但把日志前缀、配置前缀和 `event.category` 改为 `yierloom`。事件输出异常被 Core 捕获并限频记录，不反馈到插件或业务线程。

## 故障隔离

### 发现与生命周期

- 无法读取的 JAR、非法 Service Provider、错误 Descriptor、API 不兼容和错误配置只拒绝对应插件。
- 插件的 `instrumentations()`、`start()` 和 `stop()` 分别设置异常边界。
- 插件部分启动后失败时，执行逆序补偿并取消托管资源。
- 启动完成后输出一次插件状态摘要，包括启用、禁用和失败原因，但不输出配置值。

### Transformation

- Core 为每个 instrumentation module 单独持有 Transformer，避免一个 module 的安装故障阻断其他 module。
- matcher、Helper 注入或 Transformer 对某个类失败时，记录插件 ID、module ID 和目标类，目标类以未应用该 module 的字节码继续加载。
- 错误日志按插件、module 和错误阶段聚合，第一次立即记录，随后最多每 60 秒输出一次累计摘要。
- Advice 与 Bridge 双重抑制普通增强异常，增强失败不得改变业务返回值或异常。

### Runtime

- EventSink 永不阻塞调用线程，过载时只丢弃诊断事件。
- Observation 入队和分发异常不传播到 Advice；Handler 成功处理一次会重置连续失败计数，连续三次失败后自动解除，其余插件继续处理。
- ManagedScheduler 捕获任务异常；连续三次失败后取消该任务，成功一次重置计数。
- `start()`、`stop()` 和自定义插件代码仍是可信代码边界，Core 不尝试使用已废弃的强制线程终止手段。
- Core 不刻意吞掉 `VirtualMachineError` 或 `ThreadDeath`；其余插件错误遵循 fail-open。

如果 Core 级初始化或公共事件管线安装失败，Core 回滚所有已启动插件并把整个 Agent 切换为 disabled。宿主 `main` 仍继续启动。

## 内置插件迁移

每个现有 Probe 迁移为独立的内置 `YierLoomPlugin`：

| 插件 ID | 能力 | 默认状态 |
| --- | --- | --- |
| `method` | Instrumentation + Runtime aggregation | enabled |
| `exception` | Instrumentation + Runtime aggregation | enabled |
| `http` | Instrumentation + Runtime aggregation | disabled |
| `jdbc` | Instrumentation + Runtime aggregation | disabled |
| `redis` | Instrumentation + Runtime aggregation | disabled |
| `kafka` | Instrumentation + Runtime aggregation | disabled |
| `thread` | Runtime task | enabled |
| `jvm` | Runtime task | enabled |

`method` 和 `exception` 各自拥有 matcher、Advice 和状态，不通过插件间调用共享生命周期。HTTP、JDBC、Redis 和 Kafka 可以复用 `com.nowcoder.yierloom.plugins` 下的内部无状态工具，但不能绕过 SPI 访问 Core 私有入口。

Instrumentation 类匹配必须硬排除 JDK、日志框架、Byte Buddy 和 YierLoom 自身实现包；JDK 范围同时覆盖 `java.*`、`javax.*`、`sun.*`、`com.sun.*` 与 `jdk.*`，避免默认 `includes=*` 增强 JDK 内部实现类。

旧配置按职责拆入插件 namespace：

| 旧配置职责 | 新配置位置 |
| --- | --- |
| Agent enabled | `yierloom.enabled` |
| Probe 列表 | `yierloom.plugins.<id>.enabled` |
| method includes/excludes、采样、限流、慢调用和汇总 | `yierloom.plugins.method.*` |
| exception includes/excludes、采样和限流 | `yierloom.plugins.exception.*` |
| HTTP/JDBC/Redis/Kafka 阈值、采样和限流 | 对应 `yierloom.plugins.<id>.*` |
| thread snapshot interval | `yierloom.plugins.thread.snapshot-interval` |
| JVM summary interval | `yierloom.plugins.jvm.summary-interval` |

这是一次明确的命名和架构切换。生产 artifact、Java 包、配置、日志前缀和事件分类不保留 `runtime-diagnostics-agent` / `runtime.diagnostics` 兼容别名，避免形成长期双命名体系。设计文档可以提及旧模块作为迁移来源。

## Plugin Testkit

`yierloom-plugin-testkit` 提供可从插件测试中调用的契约验证器，至少覆盖：

1. JAR 中恰好存在一个 `YierLoomPlugin` Provider。
2. Descriptor 字段、ID 和 API 版本合法。
3. Provider 至少声明一种有效能力，module ID 在插件内唯一。
4. JAR 未包含 Plugin API、SDK、Byte Buddy、Core、Bootstrap 或内置插件包中的类。
5. 插件可在隔离 ClassLoader 中实例化并完成生命周期。
6. Observation Handler 只能接收本插件消息，并在停止后解除。
7. Advice enter/exit 设置 Throwable suppression。
8. Helper 声明覆盖转换后字节码需要的依赖闭包。
9. Helper 不引用 Core、SDK、Byte Buddy 或插件实例类型。

Testkit 不执行安全审计，也不能证明任意自定义 Transformer 一定正确；它验证可自动化的结构和二进制兼容契约。

## 测试策略

| 层级 | 核心验证 |
| --- | --- |
| API/SDK 单元测试 | Descriptor、API 版本、类型化配置和贡献契约 |
| Core 单元测试 | 发现、排序、重复 ID、配置隔离、生命周期回滚、Observation 路由、任务取消和消息丢弃计数 |
| ClassLoader 测试 | API/SDK/Byte Buddy 共享，Core 隐藏，插件依赖 child-first 隔离 |
| Testkit 自测 | Provider 数量、禁止打包、Advice suppression 和 Helper 闭包 |
| Forked JVM 内置插件测试 | 单一 Agent JAR 启动，八个内置插件保持事件、异常和隐私语义 |
| Forked JVM 外部插件测试 | 从目录加载测试插件，验证 Advice Observation、Runtime aggregation 和最终事件输出 |
| 冲突依赖测试 | 两个插件携带同一库的不同版本并同时工作 |
| 故障测试 | 损坏 JAR、重复 ID、错误配置、start/stop 异常和 transformation 异常不影响宿主 |
| 可见性测试 | Advice 和 Helper 在 System 及自定义应用 ClassLoader 中运行 |
| 禁用测试 | Agent disabled 时不安装 Transformer、不创建后台任务 |
| 包装测试 | Manifest、嵌套 JAR、Service Provider 和禁止重复共享依赖正确 |

外部测试插件只作为测试 fixture 构建，不增加生产模块。完整验证命令为：

```bash
cd backend
mvn -f yierloom/pom.xml verify
```

## 迁移顺序

1. 建立 Maven 聚合模块、Plugin API、Byte Buddy SDK 和 Testkit 的最小契约。
2. 建立 Bootstrap、嵌套 JAR 提取、Engine ClassLoader、外部 Plugin ClassLoader 和配置模型。
3. 用一个同时具有 Runtime 与 Instrumentation 能力的测试插件打通端到端加载。
4. 建立 Observation/事件管线、Bridge、Helper 注入、Transformer 管理和生命周期回滚。
5. 逐个迁移八个现有 Probe 为内置插件，并迁移现有单元和 forked-JVM 测试。
6. 切换 artifact、包名、配置、日志和文档，删除旧模块实现。
7. 运行完整 reactor 验证及外部插件隔离、冲突和故障场景。

迁移可以分步骤实现，但主分支最终只保留 YierLoom 入口，不同时发布新旧两个 Agent。

## 验收标准

1. `yierloom-agent.jar` 可以作为唯一 `-javaagent` 产物在 Java 17 JVM 启动。
2. 向配置目录新增一个合规插件 fat JAR 并重启 JVM 后，插件生效，Agent JAR 的内容和校验和不变。
3. Runtime-only、Instrumentation-only 和组合插件都能通过同一根 SPI 工作。
4. 组合插件的 Advice Observation 只能到达同插件 Handler，并能驱动聚合和最终事件输出。
5. 两个插件携带冲突版本的私有依赖时都能正确运行。
6. SPI 不暴露原始 `Instrumentation`；外部插件通过正常链接不能依赖 Core 或 Bootstrap 私有包，Testkit 能检出这类静态依赖。
7. Advice 和 Helper 可以跨 System 及自定义应用 ClassLoader 运行，不出现类型身份冲突。
8. 损坏或失败的插件不阻止宿主应用启动，其他插件继续运行。
9. Agent disabled 时不安装 Transformer、不启动事件 consumer 或插件任务。
10. 八个内置插件保留现有诊断 action、采样、限流、汇总、异常传播和隐私处理行为。
11. `mvn -f yierloom/pom.xml verify` 通过全部单元、契约、打包和 forked-JVM 测试。
