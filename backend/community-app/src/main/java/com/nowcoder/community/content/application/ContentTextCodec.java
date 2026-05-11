package com.nowcoder.community.content.application;

import org.springframework.stereotype.Component;

@Component
public class ContentTextCodec {

    public String escapeOnWrite(String text) {
        return text;
    }

    public String decodeOnRead(String text) {
        return text;
    }
}
