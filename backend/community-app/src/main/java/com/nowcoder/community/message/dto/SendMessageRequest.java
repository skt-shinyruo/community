package com.nowcoder.community.message.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.constants.ValidationLimits;

public class SendMessageRequest {

    @Min(1)
    private Integer toId;

    @Size(max = ValidationLimits.USERNAME_MAX)
    private String toName;

    @NotBlank
    @Size(max = ValidationLimits.MESSAGE_CONTENT_MAX)
    private String content;

    public Integer getToId() {
        return toId;
    }

    public void setToId(Integer toId) {
        this.toId = toId;
    }

    public String getToName() {
        return toName;
    }

    public void setToName(String toName) {
        this.toName = toName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
