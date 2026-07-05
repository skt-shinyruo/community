package com.nowcoder.community.content.infrastructure.text;

import com.nowcoder.community.content.application.ContentTextCodec;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class SpringHtmlContentTextCodec implements ContentTextCodec {

    @Override
    public String escapeOnWrite(String text) {
        return text == null ? null : HtmlUtils.htmlEscape(text);
    }

    @Override
    public String decodeOnRead(String text) {
        return text == null ? null : HtmlUtils.htmlUnescape(text);
    }
}
