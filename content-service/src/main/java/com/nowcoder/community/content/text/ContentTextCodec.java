package com.nowcoder.community.content.text;

import com.nowcoder.community.common.text.HtmlEntityCodec;
import com.nowcoder.community.content.config.ContentRenderProperties;
import org.springframework.stereotype.Component;

/**
 * content-service 内部统一的“写入/读出文本兼容”工具：
 * - 写入：按配置对 '&' 做最小化 escape
 * - 读出：按配置对历史 entity 做一次性白名单解码
 */
@Component
public class ContentTextCodec {

    private final ContentRenderProperties properties;

    public ContentTextCodec(ContentRenderProperties properties) {
        this.properties = properties;
    }

    public String escapeOnWrite(String text) {
        if (properties == null || !properties.isEscapeAmpersandOnWriteEnabled()) {
            return text;
        }
        return HtmlEntityCodec.escapeAmpersand(text);
    }

    public String decodeOnRead(String text) {
        if (properties == null || !properties.isLegacyEntityUnescapeEnabled()) {
            return text;
        }
        return HtmlEntityCodec.unescapeBasic(text);
    }
}

