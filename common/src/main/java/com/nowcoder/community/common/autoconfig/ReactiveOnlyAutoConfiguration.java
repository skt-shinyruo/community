package com.nowcoder.community.common.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

/**
 * reactive 专用自动装配（占位）：
 * - 当前 common 的核心跨服务能力主要面向 Servlet（Filter/ExceptionHandler）；
 * - gateway 已在自身模块实现 trace、审计、限流等 reactive 能力；
 * - 预留该入口以便后续下沉 reactive 通用能力（需避免 ThreadLocal 语义陷阱）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveOnlyAutoConfiguration {
}

