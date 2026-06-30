package com.nowcoder.community.content.application;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class ContentTextCodec {

    public String escapeOnWrite(String text) {
        return text == null ? null : HtmlUtils.htmlEscape(text);
    }

    public String decodeOnRead(String text) {
        return text == null ? null : HtmlUtils.htmlUnescape(text);
    }
}
