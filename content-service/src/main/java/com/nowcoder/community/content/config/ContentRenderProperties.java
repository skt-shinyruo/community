package com.nowcoder.community.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内容渲染/兼容策略配置（content-service）：
 * - 历史数据兼容（读路径一次性解码）
 * - 新写入策略（仅对 '&' 做最小化 escape，避免用户输入 literal entity 语义变化）
 */
@Component
@ConfigurationProperties(prefix = "content.render")
public class ContentRenderProperties {

    /**
     * 是否对“历史数据（曾写入 htmlEscape）”在读路径做基础 entity 解码。
     *
     * <p>默认开启：用于修复 &amp;lt; 等二次转义可见问题，并保持契约为“返回 text 语义”。</p>
     */
    private boolean legacyEntityUnescapeEnabled = true;

    /**
     * 是否在写入阶段仅对 '&' 做最小化 escape（& -> &amp;）。
     *
     * <p>默认开启：用于保证当开启 legacyEntityUnescapeEnabled 时，用户输入 literal entity（如 &lt;）
     * 在解码后仍保持为 &lt;（而不会变为 &lt; -> &lt; -> &lt;? 的语义变化）。</p>
     */
    private boolean escapeAmpersandOnWriteEnabled = true;

    public boolean isLegacyEntityUnescapeEnabled() {
        return legacyEntityUnescapeEnabled;
    }

    public void setLegacyEntityUnescapeEnabled(boolean legacyEntityUnescapeEnabled) {
        this.legacyEntityUnescapeEnabled = legacyEntityUnescapeEnabled;
    }

    public boolean isEscapeAmpersandOnWriteEnabled() {
        return escapeAmpersandOnWriteEnabled;
    }

    public void setEscapeAmpersandOnWriteEnabled(boolean escapeAmpersandOnWriteEnabled) {
        this.escapeAmpersandOnWriteEnabled = escapeAmpersandOnWriteEnabled;
    }
}

