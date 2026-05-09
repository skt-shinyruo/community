package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public class PostContentBlockRequest {

    private String type;

    @Size(max = ValidationLimits.POST_BLOCK_TEXT_MAX)
    private String text;

    private UUID assetId;

    @Size(max = ValidationLimits.POST_BLOCK_LANGUAGE_MAX)
    private String language;

    @Size(max = ValidationLimits.POST_BLOCK_CAPTION_MAX)
    private String caption;

    @Size(max = ValidationLimits.POST_BLOCK_DISPLAY_NAME_MAX)
    private String displayName;

    private Map<String, Object> metadata;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
