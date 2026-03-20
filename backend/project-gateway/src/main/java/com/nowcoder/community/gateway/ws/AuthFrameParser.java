package com.nowcoder.community.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthFrameParser {

    private final ObjectMapper objectMapper;

    public AuthFrameParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedAuthFrame parse(String text) {
        try {
            JsonNode node = objectMapper.readTree(text == null ? "" : text);
            String type = node.path("type").asText("");
            String accessToken = node.path("accessToken").asText("");
            if (!"auth".equals(type) || !StringUtils.hasText(accessToken)) {
                throw new IllegalArgumentException("invalid auth frame");
            }
            return new ParsedAuthFrame(accessToken.trim());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid auth frame", e);
        }
    }

    public record ParsedAuthFrame(String accessToken) {
    }
}
