package com.nowcoder.community.content.infrastructure.text;

import com.nowcoder.community.content.application.ContentSanitizer;
import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SensitiveFilter implements ContentSanitizer {

    private static final Logger log = LoggerFactory.getLogger(SensitiveFilter.class);
    private static final String REPLACEMENT = "***";

    private final TrieNode rootNode = new TrieNode();
    private final Environment environment;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger loadedWords = new AtomicInteger(0);

    public SensitiveFilter(Environment environment, MeterRegistry meterRegistry) {
        this.environment = environment;
        this.meterRegistry = meterRegistry;
        if (this.meterRegistry != null) {
            this.meterRegistry.gauge("content_sensitive_words_loaded", loadedWords);
        }
    }

    @PostConstruct
    public void init() {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("sensitive-words.txt");
        if (is == null) {
            if (isDev()) {
                log.warn("[sensitive-filter] sensitive-words.txt 缺失（dev 下允许启动，但过滤将退化为空词典）");
                loadedWords.set(0);
                return;
            }
            throw new IllegalStateException("sensitive-words.txt 缺失（非 dev 环境必须 fail-fast）");
        }
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String keyword;
            while ((keyword = reader.readLine()) != null) {
                if (!StringUtils.hasText(keyword)) {
                    continue;
                }
                addKeyword(keyword.trim());
                count++;
            }
        } catch (IOException | RuntimeException e) {
            if (isDev()) {
                log.warn("[sensitive-filter] 词典加载失败（dev 下允许继续）：{}", e.toString());
                loadedWords.set(0);
                return;
            }
            throw new IllegalStateException("sensitive-words.txt 加载失败（非 dev 环境必须 fail-fast）", e);
        }
        loadedWords.set(count);
        log.info("[sensitive-filter] loadedWords={}", count);
    }

    @Override
    public String filter(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        TrieNode tempNode = rootNode;
        int begin = 0;
        int position = 0;
        StringBuilder sb = new StringBuilder();

        while (position < text.length()) {
            char c = text.charAt(position);

            if (isSymbol(c)) {
                if (tempNode == rootNode) {
                    sb.append(c);
                    begin++;
                }
                position++;
                continue;
            }

            tempNode = tempNode.getSubNode(c);
            if (tempNode == null) {
                sb.append(text.charAt(begin));
                position = ++begin;
                tempNode = rootNode;
            } else if (tempNode.isKeywordEnd()) {
                sb.append(REPLACEMENT);
                begin = ++position;
                tempNode = rootNode;
            } else {
                position++;
            }
        }

        sb.append(text.substring(begin));
        return sb.toString();
    }

    private void addKeyword(String keyword) {
        TrieNode tempNode = rootNode;
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            TrieNode subNode = tempNode.getSubNode(c);
            if (subNode == null) {
                subNode = new TrieNode();
                tempNode.addSubNode(c, subNode);
            }
            tempNode = subNode;
            if (i == keyword.length() - 1) {
                tempNode.setKeywordEnd(true);
            }
        }
    }

    private boolean isSymbol(char c) {
        if (Character.isLetterOrDigit(c)) {
            return false;
        }
        return c < 0x2E80 || c > 0x9FFF;
    }

    private boolean isDev() {
        if (environment == null) {
            return false;
        }
        for (String p : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    private static class TrieNode {
        private boolean keywordEnd;
        private final Map<Character, TrieNode> subNodes = new HashMap<>();

        public boolean isKeywordEnd() {
            return keywordEnd;
        }

        public void setKeywordEnd(boolean keywordEnd) {
            this.keywordEnd = keywordEnd;
        }

        public void addSubNode(Character c, TrieNode node) {
            subNodes.put(c, node);
        }

        public TrieNode getSubNode(Character c) {
            return subNodes.get(c);
        }
    }
}
