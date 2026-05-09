package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.command.PostContentBlockCommand;
import com.nowcoder.community.content.application.result.PostContentBlockResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

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
    void validateCollectionShouldMatchListBehavior() {
        List<PostContentBlockCommand> blocks = List.of(
                new PostContentBlockCommand("paragraph", "hello", null, null, "", "", null),
                new PostContentBlockCommand("image", "", uuid(21), null, "chart", "", null)
        );
        Collection<PostContentBlockCommand> collectionBlocks = blocks;

        List<PostContentBlockCommand> listNormalized = policy.validateAndNormalize(blocks);
        List<PostContentBlockCommand> collectionNormalized = policy.validateAndNormalize(collectionBlocks);

        assertThat(collectionNormalized).isEqualTo(listNormalized);
    }

    @Test
    void validateShouldNormalizeUppercaseMediaTypeUnderTurkishDefaultLocale() {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            List<PostContentBlockCommand> normalized = policy.validateAndNormalize(List.of(
                    new PostContentBlockCommand("IMAGE", "", uuid(31), null, "", "", null)
            ));

            assertThat(normalized.get(0).type()).isEqualTo("image");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void validateShouldRejectEmptyBlocks() {
        assertThatThrownBy(() -> policy.validateAndNormalize(List.<PostContentBlockCommand>of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("帖子内容不能为空");
    }

    @Test
    void validateShouldRejectNullBlockElement() {
        List<PostContentBlockCommand> blocks = new ArrayList<>();
        blocks.add(null);

        assertThatThrownBy(() -> policy.validateAndNormalize(blocks))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容块非法");
    }

    @Test
    void validateShouldRejectTooManyBlocks() {
        List<PostContentBlockCommand> blocks = IntStream.range(0, 81)
                .mapToObj(index -> new PostContentBlockCommand(
                        "paragraph",
                        "block " + index,
                        null,
                        null,
                        "",
                        "",
                        null
                ))
                .toList();

        assertThatThrownBy(() -> policy.validateAndNormalize(blocks))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容块数量过多");
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

    @Test
    void commandShouldDefensivelyCopyMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("width", 640);

        PostContentBlockCommand command = new PostContentBlockCommand(
                "image",
                "",
                uuid(41),
                null,
                "",
                "",
                metadata
        );
        metadata.put("width", 1280);
        metadata.put("height", 720);

        assertThat(command.metadata())
                .containsEntry("width", 640)
                .doesNotContainKey("height");
        assertThatThrownBy(() -> command.metadata().put("mutated", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resultShouldDefensivelyCopyMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("duration", 12);

        PostContentBlockResult result = new PostContentBlockResult(
                uuid(51),
                0,
                "video",
                "",
                uuid(52),
                "",
                "",
                "",
                metadata,
                null
        );
        metadata.put("duration", 30);
        metadata.put("mutated", true);

        assertThat(result.metadata())
                .containsEntry("duration", 12)
                .doesNotContainKey("mutated");
        assertThatThrownBy(() -> result.metadata().put("mutatedAgain", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
