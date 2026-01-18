package com.nowcoder.community.content.util;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class SensitiveFilter {

    private static final String REPLACEMENT = "***";

    private final TrieNode rootNode = new TrieNode();

    @PostConstruct
    public void init() {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("sensitive-words.txt");
        if (is == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String keyword;
            while ((keyword = reader.readLine()) != null) {
                if (!StringUtils.hasText(keyword)) {
                    continue;
                }
                addKeyword(keyword.trim());
            }
        } catch (Exception ignored) {
        }
    }

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

