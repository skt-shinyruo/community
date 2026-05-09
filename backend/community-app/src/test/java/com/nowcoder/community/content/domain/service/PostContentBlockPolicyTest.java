package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.command.PostContentBlockCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostContentBlockPolicyTest {

    private final PostContentBlockPolicy policy = new PostContentBlockPolicy();

    @Test
    void validateShouldAcceptParagraphImageVideoFileAndCodeBlocks() {
        UUID imageAssetId = uuid(11);
        UUID videoAssetId = uuid(12);
        UUID fileAssetId = uuid(13);

        List<PostContentBlockCommand> normalized = policy.validateAndNormalize(List.of(
                new PostContentBlockCommand("paragraph", "hello", null, null, "", "", null),
                new PostContentBlockCommand("image", "", imageAssetId, null, "chart", "", null),
                new PostContentBlockCommand("video", "", videoAssetId, null, "demo", "", null),
                new PostContentBlockCommand("file", "", fileAssetId, null, "", "logs.zip", null),
                new PostContentBlockCommand("code", "System.out.println(1);", null, "java", "", "", null)
        ));

        assertThat(normalized).hasSize(5);
        assertThat(normalized.get(0).type()).isEqualTo("paragraph");
        assertThat(normalized.get(0).text()).isEqualTo("hello");
        assertThat(normalized.get(1).assetId()).isEqualTo(imageAssetId);
        assertThat(normalized.get(3).displayName()).isEqualTo("logs.zip");
        assertThat(normalized.get(4).language()).isEqualTo("java");
    }

    @Test
    void validateShouldRejectEmptyBlocks() {
        assertThatThrownBy(() -> policy.validateAndNormalize(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("帖子内容不能为空");
    }

    @Test
    void validateShouldRejectTextBlockWithoutText() {
        assertThatThrownBy(() -> policy.validateAndNormalize(List.of(
                new PostContentBlockCommand("paragraph", "   ", null, null, "", "", null)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文本块不能为空");
    }

    @Test
    void validateShouldRejectMediaBlockWithoutAsset() {
        assertThatThrownBy(() -> policy.validateAndNormalize(List.of(
                new PostContentBlockCommand("image", "", null, null, "", "", null)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("媒体块缺少资源");
    }

    @Test
    void validateShouldRejectUnknownBlockType() {
        assertThatThrownBy(() -> policy.validateAndNormalize(List.of(
                new PostContentBlockCommand("poll", "x", null, null, "", "", null)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的内容块类型");
    }
}
