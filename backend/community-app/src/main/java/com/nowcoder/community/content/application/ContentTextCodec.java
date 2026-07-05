package com.nowcoder.community.content.application;

public interface ContentTextCodec {

    String escapeOnWrite(String text);

    String decodeOnRead(String text);
}
