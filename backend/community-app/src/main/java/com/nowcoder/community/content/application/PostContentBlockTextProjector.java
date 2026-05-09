package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.PostContentBlock;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PostContentBlockTextProjector {

    public String fullText(List<PostContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        return blocks.stream()
                .map(this::blockText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    public String preview(List<PostContentBlock> blocks, int maxChars) {
        String fullText = fullText(blocks);
        if (fullText.length() <= maxChars) {
            return fullText;
        }
        return fullText.substring(0, Math.max(0, maxChars));
    }

    private String blockText(PostContentBlock block) {
        if (block == null || block.type() == null) {
            return "";
        }
        return switch (block.type().toLowerCase()) {
            case "paragraph", "code" -> safe(block.text());
            case "image", "video" -> safe(block.caption());
            case "file" -> safe(block.displayName());
            default -> "";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
