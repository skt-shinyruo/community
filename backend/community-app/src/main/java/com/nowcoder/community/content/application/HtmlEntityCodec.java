package com.nowcoder.community.content.application;

import org.springframework.util.StringUtils;

/**
 * HTML entity 编解码（仅基础白名单，避免过度解码）：
 * - 仅处理常见的 5 种 entity：&lt; &gt; &quot; &#39; &amp;
 * - 解码顺序保证：&amp; 最后处理，避免 "&amp;lt;" 被解成 "<"（应为 "&lt;"）。
 *
 * <p>用途：
 * 1) 兼容历史数据（曾在写入阶段做过 htmlEscape 导致内容以 entity 形式存储）；
 * 2) 防止“用户输入 literal entity（如 &lt;）”在解码后语义变化：
 *    - 写入阶段对 '&' 做一次 escape（& -> &amp;），保证输出解码后仍保持 literal entity 语义。
 */
public final class HtmlEntityCodec {

    private HtmlEntityCodec() {
    }

    /**
     * 写入阶段最小化转义：仅把 '&' 转为 '&amp;'，用于保留用户输入的 entity 语义。
     *
     * <p>说明：不要在这里做全量 htmlEscape（&lt; &gt; 等），否则会导致前端再次 escape 时出现二次转义问题。</p>
     */
    public static String escapeAmpersand(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String s = text;
        if (s.indexOf('&') < 0) {
            return s;
        }
        return s.replace("&", "&amp;");
    }

    /**
     * 读路径/对外输出阶段的“一次性基础解码”。
     *
     * <p>注意：该方法只做单次解码（不会递归），且只处理白名单实体。</p>
     */
    public static String unescapeBasic(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String s = text;
        if (s.indexOf('&') < 0) {
            return s;
        }

        // 顺序非常重要：先解 < > quotes，再解 &amp;
        s = s.replace("&lt;", "<").replace("&gt;", ">");
        s = s.replace("&quot;", "\"").replace("&#39;", "'");

        // 兼容部分来源可能使用 &apos;
        s = s.replace("&apos;", "'");

        // 最后解码 &amp;，保证 "&amp;lt;" -> "&lt;"（而不是 "<"）
        s = s.replace("&amp;", "&");
        return s;
    }
}
