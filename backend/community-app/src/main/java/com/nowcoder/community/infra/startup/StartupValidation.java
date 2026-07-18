package com.nowcoder.community.infra.startup;

import com.nowcoder.community.common.net.TrustedProxyChain;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 生产环境启动期校验（fail-closed）：
 * - 缺失关键配置直接阻断启动（避免 silent fallback）
 * - 输出缺失项清单与修复指引，降低排障成本
 */
public class StartupValidation {

    private final List<StartupValidator> validators;

    public StartupValidation() {
        this(null);
    }

    public StartupValidation(List<StartupValidator> validators) {
        this.validators = validators == null ? List.of() : List.copyOf(validators);
    }

    public void validateOrThrow(Environment environment) {
        if (environment == null) {
            return;
        }
        if (!isProd(environment)) {
            return;
        }

        String appName = getTrimmed(environment, "spring.application.name");
        List<String> errors = new ArrayList<>();

        // 1) JWT HMAC secret：所有服务都依赖（网关/服务验签 + 授权）
        String jwtSecret = getTrimmed(environment, "security.jwt.hmac-secret");
        if (!StringUtils.hasText(jwtSecret)) {
            errors.add("缺失配置：security.jwt.hmac-secret（建议设置环境变量 JWT_HMAC_SECRET 或 <SERVICE>_JWT_HMAC_SECRET，长度 >= 32）");
        } else if (jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            errors.add("配置不安全：security.jwt.hmac-secret 长度不足（建议 >= 32 字节）");
        }

        validateNacosConfig(environment, errors);

        // 1.5) trusted-proxy（可选）：启用后必须配置 CIDR allowlist，避免信任 XFF 造成伪造风险。
        validateTrustedProxy(environment, errors);

        // 1.6) social strict chain：当前只支持 DB-backed storage。
        validateSocialStorage(environment, errors);

        // 2) 服务特有的 prod 约束：由各服务自己提供 StartupValidator（避免 common 变大杂烩）。
        for (StartupValidator validator : validators) {
            if (validator == null) {
                continue;
            }
            validator.validate(environment, errors);
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[startup-validation] 生产环境启动校验失败（fail-closed），服务拒绝启动。").append('\n');
            sb.append("activeProfiles=").append(Arrays.toString(environment.getActiveProfiles())).append('\n');
            sb.append("applicationName=").append(appName).append('\n');
            sb.append("missingOrInvalid=").append('\n');
            for (String e : errors) {
                sb.append(" - ").append(e).append('\n');
            }
            sb.append("fixGuide=").append('\n');
            sb.append(" - 如果 community.nacos.config.required=true，检查 NACOS_CONFIG_IMPORT_SHARED / NACOS_CONFIG_IMPORT_SERVICE 是否使用 required nacos: dataId").append('\n');
            sb.append(" - 检查 Nacos dataId 是否已发布到正确 namespace/group").append('\n');
            sb.append(" - 检查 deploy/.env.single / deploy/.env.cluster 与部署平台 Secret/ConfigMap 是否已注入对应环境变量").append('\n');
            throw new IllegalStateException(sb.toString());
        }
    }

    private boolean isProd(Environment environment) {
        if (environment == null) {
            return false;
        }
        for (String p : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    private void requireNonBlank(Environment env, List<String> errors, String key, String hint) {
        String v = getTrimmed(env, key);
        if (!StringUtils.hasText(v)) {
            errors.add("缺失配置：" + key + "（" + hint + "）");
        }
    }

    private void validateNacosConfig(Environment environment, List<String> errors) {
        Boolean required = environment.getProperty("community.nacos.config.required", Boolean.class, Boolean.FALSE);
        if (!Boolean.TRUE.equals(required)) {
            return;
        }
        requireNonBlank(environment, errors, "NACOS_CONFIG_IMPORT_SHARED",
                "生产环境启用 Nacos Config required 模式时必须导入 community-shared.yaml");
        requireNonBlank(environment, errors, "NACOS_CONFIG_IMPORT_SERVICE",
                "生产环境启用 Nacos Config required 模式时必须导入当前服务 dataId");
    }

    private void requireTrue(Environment env, List<String> errors, String key, String hint) {
        Boolean v = env == null ? null : env.getProperty(key, Boolean.class);
        if (v == null || !v) {
            errors.add("配置不安全：" + key + "=false（" + hint + "）");
        }
    }

    private void requireFalse(Environment env, List<String> errors, String key, String hint) {
        Boolean v = env == null ? null : env.getProperty(key, Boolean.class, Boolean.FALSE);
        if (v != null && v) {
            errors.add("配置不安全：" + key + "=true（" + hint + "）");
        }
    }

    private void requireOneOf(Environment env, List<String> errors, String key, List<String> allowed, String hint) {
        String v = getTrimmed(env, key);
        if (!StringUtils.hasText(v)) {
            errors.add("缺失配置：" + key + "（" + hint + "）");
            return;
        }
        for (String a : allowed) {
            if (a.equalsIgnoreCase(v)) {
                return;
            }
        }
        errors.add("配置不合法：" + key + "=" + v + "（允许值=" + allowed + "；" + hint + "）");
    }

    private void validateTrustedProxy(Environment environment, List<String> errors) {
        if (environment == null) {
            return;
        }
        Boolean enabled = environment.getProperty("community.web.trusted-proxy.enabled", Boolean.class, Boolean.FALSE);
        if (enabled == null || !enabled) {
            return;
        }

        List<String> cidrs = bindTrustedProxyCidrs(environment, errors);
        if (cidrs == null) {
            return;
        }
        if (cidrs.isEmpty()) {
            errors.add("配置不安全：community.web.trusted-proxy.enabled=true 但 community.web.trusted-proxy.cidrs 为空（必须配置可信代理 CIDR allowlist，例如 10.0.0.0/8）");
            return;
        }

        for (int i = 0; i < cidrs.size(); i++) {
            String cidr = cidrs.get(i);
            if (!StringUtils.hasText(cidr)) {
                errors.add("配置不合法：community.web.trusted-proxy.cidrs[" + i + "] 为空");
                continue;
            }
            String trimmed = cidr.trim();
            if ("0.0.0.0/0".equals(trimmed) || "::/0".equals(trimmed)) {
                errors.add("配置不安全：community.web.trusted-proxy.cidrs[" + i + "]=" + trimmed + "（禁止使用全量信任 CIDR）");
                continue;
            }
            try {
                new TrustedProxyChain(List.of(trimmed));
            } catch (IllegalArgumentException exception) {
                errors.add("配置不合法：community.web.trusted-proxy.cidrs[" + i
                        + "] 不是有效的 IPv4/IPv6 literal CIDR（禁止 hostname、端口和 zone id）");
            }
        }
    }

    private List<String> bindTrustedProxyCidrs(Environment environment, List<String> errors) {
        try {
            return Binder.get(environment)
                    .bind("community.web.trusted-proxy.cidrs", Bindable.listOf(String.class))
                    .orElse(List.of());
        } catch (BindException exception) {
            errors.add("配置不合法：community.web.trusted-proxy.cidrs 无法绑定为 CIDR 列表"
                    + "（请使用逗号分隔字符串或 YAML list）");
            return null;
        }
    }

    private void validateSocialStorage(Environment environment, List<String> errors) {
        String socialStorage = getTrimmed(environment, "social.storage");
        if (!StringUtils.hasText(socialStorage)) {
            socialStorage = "db";
        }
        if (!"db".equalsIgnoreCase(socialStorage)) {
            errors.add("配置不安全：social.storage=" + socialStorage + "（strict social chain requires social.storage=db）");
        }
    }

    private String getTrimmed(Environment env, String key) {
        if (env == null || !StringUtils.hasText(key)) {
            return "";
        }
        String v = env.getProperty(key);
        return v == null ? "" : v.trim();
    }
}
