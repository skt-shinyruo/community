package com.nowcoder.community.message.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import com.nowcoder.community.common.validation.ValidationLimits;

import java.util.List;

public class MarkReadRequest {

    @NotEmpty
    @Size(max = ValidationLimits.IDS_MAX)
    private List<@Min(1) Integer> ids;

    public List<Integer> getIds() {
        return ids;
    }

    public void setIds(List<Integer> ids) {
        this.ids = ids;
    }
}
