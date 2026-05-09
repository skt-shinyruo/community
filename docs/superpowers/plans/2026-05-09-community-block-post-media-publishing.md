# Community Block Post Media Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace text-only post publishing with a block-based post editor and backend model that supports paragraph, image, video, file, and code blocks backed by `community-oss` media assets.

**Architecture:** `content` remains the post owner in `community-app`; post bodies are ordered `PostContentBlock` rows and media facts are `PostMediaAsset` rows. Browser upload flows enter content controllers, content application services authorize and orchestrate, and content infrastructure adapts to `community-oss-client`; no controller, domain model, or mapper calls OSS directly. There is no compatibility path for old `content`/Markdown posts because the project has no deployed data.

**Tech Stack:** Spring Boot MVC, Java records/DTOs, MyBatis XML mappers, H2/MySQL schema scripts, Maven, Vue 3, Pinia, Vitest, existing `community-oss-client`, existing upload-session frontend helper.

---

## Scope

This plan implements the full block-post vertical slice for posts only:

- backend schema and persistence for post blocks and media assets
- media upload session and proxy upload routes under `content`
- create/update/detail APIs using `blocks`, not `content`
- search projection text derived from blocks
- frontend post creation/edit/detail rendering with block editor components
- docs and focused verification

It does not implement media comments or real FFmpeg transcoding. Video states and variant tables are added now so a later worker can fill them in.

## Important Constraints

- Do not preserve old `discuss_post.content` behavior.
- Do not add legacy fallback renderers for posts.
- Do not keep post creation/update API overloads that accept `content`.
- Do not make controllers call OSS client, mappers, domain services, or repositories directly.
- Do not put Spring Web upload types in application command/result records.
- Same-domain callers must not call same-domain `api.*`; controllers call same-domain application services only.

## Files

Backend create:

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostContentBlockTextProjector.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostMediaUploadContent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/PostContentBlockCommand.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/PreparePostMediaUploadCommand.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/port/PostMediaStoragePort.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostContentBlockResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostMediaUploadSessionResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostMediaViewResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostMediaApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostContentBlockPayload.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostContentBlockView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostContentBlock.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaAsset.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaAssetLifecycle.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaKind.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostVideoState.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentBlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostMediaAssetRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostContentBlockPolicy.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/oss/OssPostMediaStorageAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentBlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostMediaAssetRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/dataobject/PostContentBlockDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/dataobject/PostMediaAssetDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostContentBlockMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostMediaAssetMapper.java`
- `backend/community-app/src/main/resources/mapper/post-content-block-mapper.xml`
- `backend/community-app/src/main/resources/mapper/post-media-asset-mapper.xml`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostContentBlockRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostContentBlockResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PreparePostMediaUploadRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostMediaUploadSessionResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostMediaUploadContentAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostMediaController.java`
- tests listed in tasks below

Backend modify:

- `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/constants/ValidationLimits.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostDraft.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/DiscussPost.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostPublishingDomainService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreatePostCommand.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostPublishingApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostDetailAssembler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostSummaryAssembler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostDetailResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostSummaryResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/action/PostPublishingActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostDetailView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostScanView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostPublishingActionApiAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostReadQueryApiAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostScanService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/DiscussPostMapper.java`
- `backend/community-app/src/main/resources/mapper/discusspost-mapper.xml`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/CreatePostRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/UpdatePostRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostDetailResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostSummaryResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/security/ContentSecurityRules.java`
- `deploy/mysql/community/040_schema_content_core.sql`
- `backend/community-app/src/test/resources/schema.sql`
- existing content/search/backend tests listed in tasks below

Frontend create:

- `frontend/src/api/services/postMediaService.js`
- `frontend/src/api/services/postMediaService.test.js`
- `frontend/src/components/posts/PostBlockEditor.vue`
- `frontend/src/components/posts/PostBlockRenderer.vue`
- `frontend/src/components/posts/PostMediaUploadBlock.vue`
- `frontend/src/components/posts/PostBlockEditor.test.js`
- `frontend/src/components/posts/PostBlockRenderer.test.js`

Frontend modify:

- `frontend/src/api/services/postService.js`
- `frontend/src/api/services/postService.test.js`
- `frontend/src/views/PostsView.vue`
- `frontend/src/views/PostsView.test.js`
- `frontend/src/views/posts/usePostsFeed.js`
- `frontend/src/views/PostDetailView.vue`
- `frontend/src/views/post-detail/usePostDetailLoader.js`
- `frontend/src/components/modals/EditContentModal.vue`
- `frontend/src/views/posts/PostsView.css`
- `frontend/src/views/post-detail/PostDetailView.css`

Docs modify:

- `docs/handbook/business-logic/content.md`
- `docs/handbook/system-design.md`

## Task 1: Backend Block Domain Types And DTO Contracts

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaKind.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaAssetLifecycle.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostVideoState.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostContentBlock.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaAsset.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostContentBlockPolicy.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/PostContentBlockCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostContentBlockResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostMediaViewResult.java`
- Modify: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/constants/ValidationLimits.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/domain/service/PostContentBlockPolicyTest.java`

- [ ] **Step 1: Write failing domain policy tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/domain/service/PostContentBlockPolicyTest.java`:

```java
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
```

- [ ] **Step 2: Run the failing policy test**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest=PostContentBlockPolicyTest test
```

Expected: compilation fails because `PostContentBlockPolicy` and `PostContentBlockCommand` do not exist.

- [ ] **Step 3: Add validation limits**

Modify `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/constants/ValidationLimits.java` content constants:

```java
// 内容相关
public static final int POST_TITLE_MAX = 128;
public static final int POST_CONTENT_BLOCKS_MAX = 80;
public static final int POST_BLOCK_TEXT_MAX = 10_000;
public static final int POST_BLOCK_CAPTION_MAX = 512;
public static final int POST_BLOCK_DISPLAY_NAME_MAX = 255;
public static final int POST_BLOCK_LANGUAGE_MAX = 64;
public static final int TAG_MAX = 32;
public static final int TAGS_MAX = 8;
public static final int COMMENT_CONTENT_MAX = 2_000;
```

Keep `POST_CONTENT_MAX` until Task 4 removes the last post-body DTO usage. In Task 4, delete `POST_CONTENT_MAX` after `rg "POST_CONTENT_MAX" backend` confirms no remaining references.

- [ ] **Step 4: Add command/result records**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/PostContentBlockCommand.java`:

```java
package com.nowcoder.community.content.application.command;

import java.util.Map;
import java.util.UUID;

public record PostContentBlockCommand(
        String type,
        String text,
        UUID assetId,
        String language,
        String caption,
        String displayName,
        Map<String, Object> metadata
) {
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostMediaViewResult.java`:

```java
package com.nowcoder.community.content.application.result;

import java.util.List;
import java.util.UUID;

public record PostMediaViewResult(
        UUID assetId,
        String mediaKind,
        String lifecycle,
        String videoState,
        String fileName,
        String contentType,
        long contentLength,
        String url,
        String downloadUrl,
        String posterUrl,
        List<VideoSource> sources
) {
    public PostMediaViewResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record VideoSource(String url, String contentType, Integer width, Integer height) {
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostContentBlockResult.java`:

```java
package com.nowcoder.community.content.application.result;

import java.util.Map;
import java.util.UUID;

public record PostContentBlockResult(
        UUID id,
        int index,
        String type,
        String text,
        UUID assetId,
        String language,
        String caption,
        String displayName,
        Map<String, Object> metadata,
        PostMediaViewResult media
) {
}
```

- [ ] **Step 5: Add domain enums and models**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaKind.java`:

```java
package com.nowcoder.community.content.domain.model;

public enum PostMediaKind {
    IMAGE,
    VIDEO,
    FILE
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaAssetLifecycle.java`:

```java
package com.nowcoder.community.content.domain.model;

public enum PostMediaAssetLifecycle {
    DRAFT,
    UPLOADED,
    BOUND,
    RELEASED,
    DELETED
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostVideoState.java`:

```java
package com.nowcoder.community.content.domain.model;

public enum PostVideoState {
    NONE,
    PENDING_TRANSCODE,
    PROCESSING,
    READY,
    FAILED
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostContentBlock.java`:

```java
package com.nowcoder.community.content.domain.model;

import java.util.Map;
import java.util.UUID;

public record PostContentBlock(
        UUID id,
        UUID postId,
        int index,
        String type,
        String text,
        UUID mediaAssetId,
        String language,
        String caption,
        String displayName,
        Map<String, Object> metadata
) {
    public PostContentBlock {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
```

Create `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaAsset.java`:

```java
package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record PostMediaAsset(
        UUID id,
        UUID ownerUserId,
        UUID postId,
        UUID ossObjectId,
        UUID ossVersionId,
        UUID ossReferenceId,
        UUID uploadSessionId,
        String fileName,
        String contentType,
        long contentLength,
        PostMediaKind mediaKind,
        PostMediaAssetLifecycle lifecycle,
        PostVideoState videoState,
        String publicUrl,
        String failureReason,
        Date createTime,
        Date updateTime
) {
}
```

- [ ] **Step 6: Add `PostContentBlockPolicy`**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostContentBlockPolicy.java`:

```java
package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.constants.ValidationLimits;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.command.PostContentBlockCommand;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Component
public class PostContentBlockPolicy {

    public List<PostContentBlockCommand> validateAndNormalize(List<PostContentBlockCommand> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "帖子内容不能为空");
        }
        if (blocks.size() > ValidationLimits.POST_CONTENT_BLOCKS_MAX) {
            throw new BusinessException(INVALID_ARGUMENT, "内容块数量过多");
        }
        return blocks.stream()
                .map(this::normalizeOne)
                .toList();
    }

    private PostContentBlockCommand normalizeOne(PostContentBlockCommand raw) {
        if (raw == null) {
            throw new BusinessException(INVALID_ARGUMENT, "内容块非法");
        }
        String type = safe(raw.type()).toLowerCase();
        return switch (type) {
            case "paragraph", "code" -> normalizeTextBlock(type, raw);
            case "image", "video", "file" -> normalizeMediaBlock(type, raw);
            default -> throw new BusinessException(INVALID_ARGUMENT, "不支持的内容块类型");
        };
    }

    private PostContentBlockCommand normalizeTextBlock(String type, PostContentBlockCommand raw) {
        String text = limit(safe(raw.text()), ValidationLimits.POST_BLOCK_TEXT_MAX, "文本块过长");
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(INVALID_ARGUMENT, "文本块不能为空");
        }
        String language = "code".equals(type) ? limit(safe(raw.language()), ValidationLimits.POST_BLOCK_LANGUAGE_MAX, "代码语言过长") : "";
        return new PostContentBlockCommand(type, text, null, language, "", "", raw.metadata());
    }

    private PostContentBlockCommand normalizeMediaBlock(String type, PostContentBlockCommand raw) {
        if (raw.assetId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体块缺少资源");
        }
        String caption = limit(safe(raw.caption()), ValidationLimits.POST_BLOCK_CAPTION_MAX, "说明文字过长");
        String displayName = limit(safe(raw.displayName()), ValidationLimits.POST_BLOCK_DISPLAY_NAME_MAX, "文件名过长");
        return new PostContentBlockCommand(type, "", raw.assetId(), "", caption, displayName, raw.metadata());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String limit(String value, int max, String message) {
        if (value.length() > max) {
            throw new BusinessException(INVALID_ARGUMENT, message);
        }
        return value;
    }
}
```

- [ ] **Step 7: Run policy tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest=PostContentBlockPolicyTest test
```

Expected: PASS.

- [ ] **Step 8: Commit task 1**

```bash
git add backend/community-common/common-core/src/main/java/com/nowcoder/community/common/constants/ValidationLimits.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/command/PostContentBlockCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostContentBlockResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostMediaViewResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostContentBlock.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaAsset.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaAssetLifecycle.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostMediaKind.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostVideoState.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostContentBlockPolicy.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/domain/service/PostContentBlockPolicyTest.java
git commit -m "feat: add post block domain model"
```

## Task 2: Schema And MyBatis Persistence For Blocks And Media

**Files:**
- Modify: `deploy/mysql/community/040_schema_content_core.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/DiscussPost.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostDraft.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/DiscussPostMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/discusspost-mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentBlockRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostMediaAssetRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/dataobject/PostContentBlockDataObject.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/dataobject/PostMediaAssetDataObject.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostContentBlockMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostMediaAssetMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/post-content-block-mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/post-media-asset-mapper.xml`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentBlockRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostMediaAssetRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostContentBlockMapperPersistenceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostMediaAssetMapperPersistenceTest.java`
- Modify test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/DiscussPostMapperPersistenceTest.java`

- [ ] **Step 1: Write failing mapper persistence tests**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostContentBlockMapperPersistenceTest.java`:

```java
package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostContentBlockDataObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PostContentBlockMapperPersistenceTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000000601");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000602");
    private static final UUID BLOCK_ID = UUID.fromString("00000000-0000-7000-8000-000000000603");
    private static final UUID MEDIA_ID = UUID.fromString("00000000-0000-7000-8000-000000000604");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PostContentBlockMapper mapper;
    @MockBean ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_content_block");
        jdbcTemplate.update("delete from post_media_asset");
        jdbcTemplate.update("delete from discuss_post");
    }

    @Test
    void insertAndListByPostIdShouldPreserveOrderAndMediaReference() {
        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, title, type, status, create_time, comment_count, score) values (?, ?, ?, ?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(POST_ID),
                BinaryUuidCodec.toBytes(USER_ID),
                "title",
                0,
                0,
                Timestamp.from(Instant.parse("2026-05-09T00:00:00Z")),
                0,
                0.0
        );

        PostContentBlockDataObject row = new PostContentBlockDataObject();
        row.setId(BLOCK_ID);
        row.setPostId(POST_ID);
        row.setBlockIndex(0);
        row.setBlockType("image");
        row.setTextContent("");
        row.setMediaAssetId(MEDIA_ID);
        row.setCaption("chart");
        row.setDisplayName("");
        row.setLanguage("");
        row.setMetadataJson("{\"layout\":\"wide\"}");
        row.setCreateTime(Timestamp.from(Instant.parse("2026-05-09T00:01:00Z")));

        assertThat(mapper.insert(row)).isEqualTo(1);

        List<PostContentBlockDataObject> rows = mapper.selectByPostId(POST_ID);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(BLOCK_ID);
        assertThat(rows.get(0).getMediaAssetId()).isEqualTo(MEDIA_ID);
        assertThat(rows.get(0).getCaption()).isEqualTo("chart");
        assertThat(rows.get(0).getMetadataJson()).contains("wide");
    }
}
```

Create `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/PostMediaAssetMapperPersistenceTest.java`:

```java
package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostMediaAssetDataObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PostMediaAssetMapperPersistenceTest {

    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000000701");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-7000-8000-000000000702");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000000703");
    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000000704");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-7000-8000-000000000705");
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-7000-8000-000000000706");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PostMediaAssetMapper mapper;
    @MockBean ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_media_asset");
    }

    @Test
    void insertMarkUploadedAndBindShouldPersistAssetLifecycle() {
        PostMediaAssetDataObject row = new PostMediaAssetDataObject();
        row.setId(ASSET_ID);
        row.setOwnerUserId(OWNER_ID);
        row.setOssObjectId(OBJECT_ID);
        row.setOssVersionId(VERSION_ID);
        row.setUploadSessionId(UUID.fromString("00000000-0000-7000-8000-000000000707"));
        row.setFileName("demo.mp4");
        row.setContentType("video/mp4");
        row.setContentLength(1234L);
        row.setMediaKind("VIDEO");
        row.setLifecycle("DRAFT");
        row.setVideoState("NONE");
        row.setPublicUrl("");
        row.setCreateTime(Timestamp.from(Instant.parse("2026-05-09T00:00:00Z")));

        assertThat(mapper.insert(row)).isEqualTo(1);
        assertThat(mapper.markUploaded(ASSET_ID, VERSION_ID, "http://localhost/files/demo.mp4", Timestamp.from(Instant.parse("2026-05-09T00:01:00Z")))).isEqualTo(1);
        assertThat(mapper.bindToPost(ASSET_ID, POST_ID, REFERENCE_ID, "PENDING_TRANSCODE", Timestamp.from(Instant.parse("2026-05-09T00:02:00Z")))).isEqualTo(1);

        PostMediaAssetDataObject saved = mapper.selectById(ASSET_ID);
        assertThat(saved.getLifecycle()).isEqualTo("BOUND");
        assertThat(saved.getPostId()).isEqualTo(POST_ID);
        assertThat(saved.getOssReferenceId()).isEqualTo(REFERENCE_ID);
        assertThat(saved.getVideoState()).isEqualTo("PENDING_TRANSCODE");
        assertThat(saved.getPublicUrl()).isEqualTo("http://localhost/files/demo.mp4");
    }
}
```

- [ ] **Step 2: Run mapper tests and verify they fail**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='PostContentBlockMapperPersistenceTest,PostMediaAssetMapperPersistenceTest' test
```

Expected: compilation fails because mappers/data objects/tables do not exist.

- [ ] **Step 3: Remove `content` from post schema and add block/media tables**

Modify both `deploy/mysql/community/040_schema_content_core.sql` and `backend/community-app/src/test/resources/schema.sql`:

1. Remove `content text` from `discuss_post`.
2. Remove seeded `content` column/value from the test seed insert in `schema.sql`.
3. Add these tables after `discuss_post`:

```sql
create table if not exists post_media_asset (
  id binary(16) primary key,
  owner_user_id binary(16) not null,
  post_id binary(16) default null,
  oss_object_id binary(16) not null,
  oss_version_id binary(16) default null,
  oss_reference_id binary(16) default null,
  upload_session_id binary(16) default null,
  file_name varchar(255) not null,
  content_type varchar(128) not null,
  content_length bigint not null,
  media_kind varchar(32) not null,
  lifecycle varchar(32) not null,
  video_state varchar(32) not null default 'NONE',
  public_url varchar(1024) default '',
  failure_reason varchar(512) default '',
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null
);

create index idx_post_media_asset_owner_lifecycle on post_media_asset(owner_user_id, lifecycle);
create index idx_post_media_asset_post on post_media_asset(post_id);
create index idx_post_media_asset_video_state on post_media_asset(video_state);

create table if not exists post_content_block (
  id binary(16) primary key,
  post_id binary(16) not null,
  block_index int not null,
  block_type varchar(32) not null,
  text_content text null,
  language varchar(64) default '',
  media_asset_id binary(16) default null,
  caption varchar(512) default '',
  display_name varchar(255) default '',
  metadata_json text default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  unique key uk_post_block_index (post_id, block_index)
);

create index idx_post_content_block_post on post_content_block(post_id);
create index idx_post_content_block_media on post_content_block(media_asset_id);
```

Use `metadata_json text default null` in both H2 and MySQL scripts for this first implementation. A later migration can change the MySQL column to native JSON after production SQL coverage exists.

- [ ] **Step 4: Update cleanup order in test schema**

In `backend/community-app/src/test/resources/schema.sql`, add deletes before `delete from discuss_post`:

```sql
delete from post_content_block;
delete from post_media_asset;
```

- [ ] **Step 5: Remove `content` from `DiscussPost` and `PostDraft`**

Modify `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/DiscussPost.java`:

- delete `private String content;`
- delete `getContent()`
- delete `setContent(String content)`

Modify `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostDraft.java`:

```java
public record PostDraft(
        UUID userId,
        String title,
        UUID categoryId,
        Date createTime
) {
}
```

- [ ] **Step 6: Update post mapper SQL**

Modify `backend/community-app/src/main/resources/mapper/discusspost-mapper.xml`:

```xml
<sql id="selectFields">
    id, user_id, category_id, title, type, status, create_time, update_time, edit_count, deleted_by, deleted_reason, deleted_time, comment_count, score
</sql>

<sql id="insertFields">
    id, user_id, category_id, title, type, status, create_time, comment_count, score
</sql>
```

Update insert values:

```xml
<insert id="insertDiscussPost" parameterType="com.nowcoder.community.content.domain.model.DiscussPost">
    insert into discuss_post(<include refid="insertFields"></include>)
    values(#{id, jdbcType=BINARY},#{userId, jdbcType=BINARY},#{categoryId, jdbcType=BINARY},#{title},#{type},#{status},#{createTime},#{commentCount},#{score})
</insert>
```

Rename `updatePostContent` SQL to update title/category only:

```xml
<update id="updatePostMeta">
    update discuss_post
    set title = #{title},
        category_id = #{categoryId, jdbcType=BINARY},
        update_time = #{updateTime},
        edit_count = edit_count + 1
    where id = #{id, jdbcType=BINARY}
</update>
```

Modify `DiscussPostMapper.java` method:

```java
int updatePostMeta(
        @Param("id") UUID id,
        @Param("title") String title,
        @Param("categoryId") UUID categoryId,
        @Param("updateTime") java.util.Date updateTime
);
```

- [ ] **Step 7: Update repositories for post metadata**

Modify `PostRepository`:

```java
UUID create(PostDraft draft);

PostSnapshot getRequiredSnapshot(UUID postId);

void updatePostMeta(UUID postId, String title, UUID categoryId, Date updateTime);
```

Modify `PostContentRepository`:

```java
void updatePostMeta(UUID postId, String title, UUID categoryId, Date updateTime);
```

Update `MyBatisPostRepository#create` to not set content:

```java
post.setTitle(draft.title());
post.setType(0);
post.setStatus(0);
post.setCreateTime(draft.createTime());
```

Update `MyBatisPostRepository#updatePostMeta` and `MyBatisPostContentRepository#updatePostMeta` to call `discussPostMapper.updatePostMeta(...)`.

- [ ] **Step 8: Add data objects and mappers**

Create `PostContentBlockDataObject` with JavaBean getters/setters for:

```java
UUID id;
UUID postId;
int blockIndex;
String blockType;
String textContent;
String language;
UUID mediaAssetId;
String caption;
String displayName;
String metadataJson;
Date createTime;
Date updateTime;
```

Create `PostMediaAssetDataObject` with JavaBean getters/setters for:

```java
UUID id;
UUID ownerUserId;
UUID postId;
UUID ossObjectId;
UUID ossVersionId;
UUID ossReferenceId;
UUID uploadSessionId;
String fileName;
String contentType;
long contentLength;
String mediaKind;
String lifecycle;
String videoState;
String publicUrl;
String failureReason;
Date createTime;
Date updateTime;
```

Create `PostContentBlockMapper.java`:

```java
package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostContentBlockDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PostContentBlockMapper {
    int insert(PostContentBlockDataObject row);
    int deleteByPostId(@Param("postId") UUID postId);
    List<PostContentBlockDataObject> selectByPostId(@Param("postId") UUID postId);
    List<PostContentBlockDataObject> selectByPostIds(@Param("postIds") List<UUID> postIds);
}
```

Create `PostMediaAssetMapper.java`:

```java
package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostMediaAssetDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mapper
public interface PostMediaAssetMapper {
    int insert(PostMediaAssetDataObject row);
    PostMediaAssetDataObject selectById(@Param("id") UUID id);
    List<PostMediaAssetDataObject> selectByIds(@Param("ids") List<UUID> ids);
    List<PostMediaAssetDataObject> selectByPostId(@Param("postId") UUID postId);
    int markUploaded(@Param("id") UUID id, @Param("versionId") UUID versionId, @Param("publicUrl") String publicUrl, @Param("updateTime") Date updateTime);
    int bindToPost(@Param("id") UUID id, @Param("postId") UUID postId, @Param("ossReferenceId") UUID ossReferenceId, @Param("videoState") String videoState, @Param("updateTime") Date updateTime);
    int releaseRemovedFromPost(@Param("postId") UUID postId, @Param("keepIds") List<UUID> keepIds, @Param("updateTime") Date updateTime);
}
```

- [ ] **Step 9: Add MyBatis XML files**

Create `backend/community-app/src/main/resources/mapper/post-content-block-mapper.xml` with insert/select/delete mapped to `post_content_block`. Use `#{id, jdbcType=BINARY}` for UUID fields and order by `block_index asc`.

Create `backend/community-app/src/main/resources/mapper/post-media-asset-mapper.xml` with insert/select/update methods. `releaseRemovedFromPost` should:

```sql
update post_media_asset
set lifecycle = 'RELEASED',
    update_time = #{updateTime}
where post_id = #{postId, jdbcType=BINARY}
<if test="keepIds != null and keepIds.size() > 0">
  and id not in
  <foreach collection="keepIds" item="id" open="(" separator="," close=")">
    #{id, jdbcType=BINARY}
  </foreach>
</if>
```

- [ ] **Step 10: Add repository interfaces and implementations**

Create `PostContentBlockRepository`:

```java
package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.PostContentBlock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PostContentBlockRepository {
    void replaceBlocks(UUID postId, List<PostContentBlock> blocks);
    List<PostContentBlock> listByPostId(UUID postId);
    Map<UUID, List<PostContentBlock>> listByPostIds(List<UUID> postIds);
}
```

Create `PostMediaAssetRepository` with methods:

```java
PostMediaAsset getRequired(UUID assetId);
List<PostMediaAsset> listByIds(List<UUID> assetIds);
List<PostMediaAsset> listByPostId(UUID postId);
UUID createDraft(PostMediaAsset asset);
void markUploaded(UUID assetId, UUID versionId, String publicUrl, Date updateTime);
void bindToPost(UUID assetId, UUID postId, UUID ossReferenceId, PostVideoState videoState, Date updateTime);
void releaseRemovedFromPost(UUID postId, List<UUID> keepIds, Date updateTime);
```

Implement both using the new mappers and `UuidV7Generator`. Mapping must translate enum names with `Enum.valueOf(...)`.

- [ ] **Step 11: Fix existing persistence tests for no `content` column**

Modify `DiscussPostMapperPersistenceTest`:

- remove `post.setContent("binary uuid");`
- keep assertions for id/category/title
- update any insert SQL references to omit `content`

- [ ] **Step 12: Run persistence tests**

Run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='DiscussPostMapperPersistenceTest,PostContentBlockMapperPersistenceTest,PostMediaAssetMapperPersistenceTest' test
```

Expected: PASS.

- [ ] **Step 13: Commit task 2**

```bash
git add deploy/mysql/community/040_schema_content_core.sql \
  backend/community-app/src/test/resources/schema.sql \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/DiscussPost.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/PostDraft.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentBlockRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostMediaAssetRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence \
  backend/community-app/src/main/resources/mapper/discusspost-mapper.xml \
  backend/community-app/src/main/resources/mapper/post-content-block-mapper.xml \
  backend/community-app/src/main/resources/mapper/post-media-asset-mapper.xml \
  backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper
git commit -m "feat: persist post blocks and media assets"
```

## Task 3: Content Media Upload Session And OSS Adapter

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostMediaUploadContent.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/PreparePostMediaUploadCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/port/PostMediaStoragePort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostMediaUploadSessionResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostMediaApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/oss/OssPostMediaStorageAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PreparePostMediaUploadRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostMediaUploadSessionResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostMediaUploadContentAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostMediaController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/security/ContentSecurityRules.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostMediaApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/oss/OssPostMediaStorageAdapterTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostMediaControllerUnitTest.java`

- [ ] **Step 1: Write failing application service tests**

Create `PostMediaApplicationServiceTest`:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.PreparePostMediaUploadCommand;
import com.nowcoder.community.content.application.port.PostMediaStoragePort;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostMediaApplicationServiceTest {

    @Test
    void prepareUploadShouldCreateDraftAssetAndReturnUploadSession() {
        UUID userId = uuid(7);
        UUID assetId = uuid(8);
        UUID objectId = uuid(9);
        UUID versionId = uuid(10);
        UUID sessionId = uuid(11);
        PostMediaAssetRepository assetRepository = mock(PostMediaAssetRepository.class);
        PostMediaStoragePort storagePort = mock(PostMediaStoragePort.class);
        when(assetRepository.createDraft(org.mockito.ArgumentMatchers.any())).thenReturn(assetId);
        when(storagePort.prepareUpload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PostMediaUploadSessionResult(
                        assetId,
                        sessionId.toString(),
                        "/api/posts/media/" + assetId + "/upload",
                        "POST",
                        "file",
                        "uploadId",
                        100 * 1024 * 1024L,
                        "image/png;image/jpeg;image/webp;image/gif;video/mp4;video/webm;application/pdf;application/zip",
                        Instant.parse("2026-05-09T00:10:00Z"),
                        objectId,
                        versionId
                ));

        PostMediaApplicationService service = new PostMediaApplicationService(assetRepository, storagePort);

        PostMediaUploadSessionResult result = service.prepareUpload(new PreparePostMediaUploadCommand(
                userId,
                "demo.mp4",
                "video/mp4",
                1234,
                "VIDEO",
                ""
        ));

        assertThat(result.assetId()).isEqualTo(assetId);
        assertThat(result.uploadUrl()).isEqualTo("/api/posts/media/" + assetId + "/upload");
        var captor = forClass(PostMediaAsset.class);
        verify(assetRepository).createDraft(captor.capture());
        assertThat(captor.getValue().ownerUserId()).isEqualTo(userId);
        assertThat(captor.getValue().mediaKind()).isEqualTo(PostMediaKind.VIDEO);
        assertThat(captor.getValue().lifecycle()).isEqualTo(PostMediaAssetLifecycle.DRAFT);
        assertThat(captor.getValue().videoState()).isEqualTo(PostVideoState.NONE);
    }
}
```

- [ ] **Step 2: Run failing application media test**

```bash
cd backend
mvn -q -pl :community-app -Dtest=PostMediaApplicationServiceTest test
```

Expected: compilation fails for missing media application classes.

- [ ] **Step 3: Add upload command/content/result/port**

Create `PreparePostMediaUploadCommand`:

```java
package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record PreparePostMediaUploadCommand(
        UUID actorUserId,
        String fileName,
        String contentType,
        long contentLength,
        String mediaKind,
        String checksumSha256
) {
}
```

Create `PostMediaUploadContent`:

```java
package com.nowcoder.community.content.application;

import java.io.IOException;
import java.io.InputStream;

public record PostMediaUploadContent(
        UploadStream uploadStream,
        String contentType,
        long size,
        String checksumSha256
) {
    public InputStream openStream() throws IOException {
        return uploadStream == null ? InputStream.nullInputStream() : uploadStream.openStream();
    }

    public boolean empty() {
        return size <= 0;
    }

    @FunctionalInterface
    public interface UploadStream {
        InputStream openStream() throws IOException;
    }
}
```

Create `PostMediaUploadSessionResult`:

```java
package com.nowcoder.community.content.application.result;

import java.time.Instant;
import java.util.UUID;

public record PostMediaUploadSessionResult(
        UUID assetId,
        String uploadId,
        String uploadUrl,
        String uploadMethod,
        String fileField,
        String uploadIdField,
        long maxBytes,
        String mimeTypes,
        Instant expiresAt,
        UUID ossObjectId,
        UUID ossVersionId
) {
}
```

Create `PostMediaStoragePort`:

```java
package com.nowcoder.community.content.application.port;

import com.nowcoder.community.content.application.PostMediaUploadContent;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;

import java.util.UUID;

public interface PostMediaStoragePort {
    PostMediaUploadSessionResult prepareUpload(PostMediaAsset draft, String checksumSha256);
    UploadedPostMedia completeUpload(PostMediaAsset draft, UUID uploadSessionId, PostMediaUploadContent content);
    UUID bindReference(PostMediaAsset asset, UUID postId, UUID actorUserId);
    void releaseReference(PostMediaAsset asset, UUID actorUserId);

    record UploadedPostMedia(UUID versionId, String publicUrl, String contentType, long contentLength) {
    }
}
```

- [ ] **Step 4: Implement `PostMediaApplicationService`**

Create service with constants:

```java
private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
private static final String MIME_TYPES = "image/png;image/jpeg;image/webp;image/gif;video/mp4;video/webm;application/pdf;application/zip";
```

Implement:

- `prepareUpload(PreparePostMediaUploadCommand command)`
  - validate actor, file name, content type, size
  - infer `PostMediaKind` from command mediaKind and content type
  - create draft with `lifecycle=DRAFT`, `videoState=NONE`
  - call repository `createDraft`
  - call storagePort `prepareUpload` using draft with assigned asset id

- `completeUpload(UUID actorUserId, UUID assetId, UUID uploadSessionId, PostMediaUploadContent content)`
  - load asset
  - require owner and draft
  - call storagePort.completeUpload
  - mark repository uploaded

If repository `createDraft` assigns id, rebuild the draft with returned id before calling storage.

- [ ] **Step 5: Write failing OSS adapter test**

Create `OssPostMediaStorageAdapterTest` mirroring avatar storage but no Redis:

```java
package com.nowcoder.community.content.infrastructure.oss;

import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssPostMediaStorageAdapterTest {

    @Test
    void prepareUploadShouldUseContentPostMediaOwnerContext() {
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        when(ossClient.prepareUpload(any())).thenReturn(new OssUploadSessionResponse(
                uuid(21), uuid(22), uuid(23), "PROXY", "/api/oss/objects/" + uuid(22) + "/complete", Instant.parse("2026-05-09T00:10:00Z")
        ));
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(ossClient);
        PostMediaAsset draft = new PostMediaAsset(
                uuid(1), uuid(2), null, uuid(22), uuid(23), null, uuid(21), "demo.mp4", "video/mp4", 12,
                PostMediaKind.VIDEO, PostMediaAssetLifecycle.DRAFT, PostVideoState.NONE, "", "", new Date(), null
        );

        var result = adapter.prepareUpload(draft, "");

        assertThat(result.uploadUrl()).isEqualTo("/api/posts/media/" + draft.id() + "/upload");
        verify(ossClient).prepareUpload(any(OssUploadSessionRequest.class));
    }
}
```

- [ ] **Step 6: Implement `OssPostMediaStorageAdapter`**

Create `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/oss/OssPostMediaStorageAdapter.java`.

Implementation rules:

- `prepareUpload` calls `ossClient.prepareUpload(new OssUploadSessionRequest("CONTENT_POST_MEDIA", "community-app", "content", "post-media-draft", draft.id().toString(), "PUBLIC", draft.fileName(), draft.contentType(), draft.contentLength(), checksumSha256, "", draft.ownerUserId().toString()))`
- Return upload URL `/api/posts/media/{assetId}/upload`, method `POST`, file field `file`, field `uploadId`.
- `completeUpload` calls `ossClient.completeProxyUpload(new OssCompleteUploadRequest(uploadSessionId, draft.ossObjectId(), draft.ossVersionId(), content::openStream, draft.fileName(), content.contentType(), content.size(), content.checksumSha256()))`.
- `bindReference` calls `ossClient.bindObjectReference(asset.ossObjectId(), new OssBindReferenceRequest(asset.ossVersionId().toString(), "community-app", "content", "post", postId.toString(), "POST_MEDIA", null, actorUserId.toString()))`.
- `releaseReference` calls `ossClient.releaseObjectReference` when `ossReferenceId` exists.

- [ ] **Step 7: Add controller DTOs and controller test**

Create `PostMediaControllerUnitTest` that:

- constructs controller with mocked `PostMediaApplicationService`
- sends `PreparePostMediaUploadRequest`
- verifies `prepareUpload` called with current user id
- verifies response shape contains `upload.url`, `upload.fileField`, `constraints.mimeTypes`

Then implement:

- `PreparePostMediaUploadRequest`
- `PostMediaUploadSessionResponse` with nested `UploadInstruction` and `Constraints`
- `PostMediaUploadContentAdapter`
- `PostMediaController`

Controller methods:

```java
@PostMapping("/upload-sessions")
public Result<PostMediaUploadSessionResponse> prepareUpload(Authentication authentication, @Valid @RequestBody PreparePostMediaUploadRequest request)

@PostMapping(value = "/{assetId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Result<Void> upload(Authentication authentication, @PathVariable UUID assetId, @RequestParam UUID uploadId, @RequestParam("file") MultipartFile file)
```

Use class mapping:

```java
@RestController
@RequestMapping("/api/posts/media")
```

- [ ] **Step 8: Update security rules**

Modify `ContentSecurityRules` so authenticated users can post to media endpoints by default. Since any unmatched endpoint already requires authentication in global security, only ensure public matchers do not accidentally permit uploads. No new `permitAll` matcher for `/api/posts/media/**`.

- [ ] **Step 9: Run media tests**

```bash
cd backend
mvn -q -pl :community-app -Dtest='PostMediaApplicationServiceTest,OssPostMediaStorageAdapterTest,PostMediaControllerUnitTest' test
```

Expected: PASS.

- [ ] **Step 10: Commit task 3**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/PostMediaUploadContent.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/command/PreparePostMediaUploadCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/port/PostMediaStoragePort.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostMediaUploadSessionResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostMediaApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/oss/OssPostMediaStorageAdapter.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PreparePostMediaUploadRequest.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostMediaUploadSessionResponse.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostMediaUploadContentAdapter.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostMediaController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/security/ContentSecurityRules.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/PostMediaApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/oss/OssPostMediaStorageAdapterTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostMediaControllerUnitTest.java
git commit -m "feat: add post media upload sessions"
```

## Task 4: Backend Block-Based Create, Update, Detail, And Projection

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreatePostCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostPublishingApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostDetailAssembler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostSummaryAssembler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostContentBlockTextProjector.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostDetailResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/CreatePostRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/UpdatePostRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostContentBlockRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostContentBlockResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/dto/PostDetailResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/api/action/PostPublishingActionApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostDetailView.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostPublishingActionApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostReadQueryApiAdapter.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostPublishingApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/api/PostPublishingActionApiAdapterTest.java`

- [ ] **Step 1: Update failing application publishing test**

Modify `PostPublishingApplicationServiceTest#createShouldOwnPublishingOrchestrationInsideApplicationLayer` to use block commands:

```java
List<PostContentBlockCommand> blocks = List.of(
        new PostContentBlockCommand("paragraph", "<content>", null, null, "", "", null)
);
PostDraft draft = new PostDraft(userId, "title", categoryId, createTime);
when(domainService.createDraft(userId, "title", categoryId)).thenReturn(draft);

PostCreateResult response = service.create(
        "idem-1",
        new CreatePostCommand(userId, "<title>", categoryId, List.of("java"), blocks)
);

inOrder.verify(domainService).createDraft(userId, "title", categoryId);
inOrder.verify(postRepository).create(draft);
inOrder.verify(postContentBlockRepository).replaceBlocks(eq(postId), any());
```

Add mocks for:

```java
private PostContentBlockPolicy blockPolicy;
private PostContentBlockRepository postContentBlockRepository;
private PostMediaAssetRepository postMediaAssetRepository;
private PostMediaStoragePort postMediaStoragePort;
```

Constructor expectations will fail until service signature is updated.

- [ ] **Step 2: Run failing publishing test**

```bash
cd backend
mvn -q -pl :community-app -Dtest=PostPublishingApplicationServiceTest test
```

Expected: compilation fails due to old `CreatePostCommand`, old service constructor, and old post draft signature.

- [ ] **Step 3: Update command and domain service signatures**

Modify `CreatePostCommand`:

```java
public record CreatePostCommand(
        UUID userId,
        String title,
        UUID categoryId,
        List<String> tags,
        List<PostContentBlockCommand> blocks
) {
}
```

Modify `PostPublishingDomainService#createDraft`:

```java
public PostDraft createDraft(UUID userId, String title, UUID categoryId) {
    if (userId == null) {
        throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
    }
    if (!org.springframework.util.StringUtils.hasText(title)) {
        throw new BusinessException(INVALID_ARGUMENT, "标题不能为空");
    }
    return new PostDraft(userId, title, categoryId, new Date());
}
```

- [ ] **Step 4: Implement block save and media binding in publishing service**

Modify `PostPublishingApplicationService` constructor to accept:

```java
PostContentBlockPolicy blockPolicy,
PostContentBlockRepository postContentBlockRepository,
PostMediaAssetRepository postMediaAssetRepository,
PostMediaStoragePort postMediaStoragePort
```

In `create(String idempotencyKey, CreatePostCommand command)`:

1. `List<PostContentBlockCommand> blocks = blockPolicy.validateAndNormalize(command.blocks());`
2. Create `PostDraft` with sanitized title and category only.
3. Create post.
4. Convert commands to `PostContentBlock` rows with generated ids or let repository assign ids.
5. Bind media assets before/after saving blocks in the same transaction:
   - collect asset ids from image/video/file blocks
   - load assets
   - require owner = command.userId
   - require lifecycle `UPLOADED`
   - require kind matches block type
   - call `postMediaStoragePort.bindReference(asset, postId, userId)`
   - repository `bindToPost(asset.id(), postId, referenceId, videoState, now)`
6. Save blocks.
7. Existing tag/points/task/event/score/log side effects remain.

For video blocks, bind with `PostVideoState.PENDING_TRANSCODE`. For image/file blocks, bind with `PostVideoState.NONE`.

Update `updatePost` similarly:

- signature becomes `updatePost(UUID userId, UUID postId, String title, UUID categoryId, List<String> tags, List<PostContentBlockCommand> blocks)`
- update post metadata
- replace blocks
- bind newly referenced uploaded assets
- release removed media by calling repository `releaseRemovedFromPost(postId, keepIds, now)`
- publish update event

- [ ] **Step 5: Update controller DTOs**

Replace `content` in `CreatePostRequest` and `UpdatePostRequest` with:

```java
@jakarta.validation.Valid
@jakarta.validation.constraints.NotEmpty
@jakarta.validation.constraints.Size(max = ValidationLimits.POST_CONTENT_BLOCKS_MAX)
private List<PostContentBlockRequest> blocks;
```

Create `PostContentBlockRequest`:

```java
package com.nowcoder.community.content.controller.dto;

import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.constants.ValidationLimits;

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

    // getters and setters
}
```

Add `toCommand()` method or static conversion in controller:

```java
private static List<PostContentBlockCommand> toBlockCommands(List<PostContentBlockRequest> blocks) {
    if (blocks == null) return List.of();
    return blocks.stream()
            .map(b -> new PostContentBlockCommand(b.getType(), b.getText(), b.getAssetId(), b.getLanguage(), b.getCaption(), b.getDisplayName(), b.getMetadata()))
            .toList();
}
```

- [ ] **Step 6: Update `PostController` write methods**

Create:

```java
PostCreateResult createResult = postPublishingApplicationService.create(
        idempotencyKey,
        new CreatePostCommand(
                userId,
                request.getTitle(),
                request.getCategoryId(),
                request.getTags(),
                toBlockCommands(request.getBlocks())
        )
);
```

Update edit:

```java
postPublishingApplicationService.updatePost(
        userId,
        postId,
        request.getTitle(),
        request.getCategoryId(),
        request.getTags(),
        toBlockCommands(request.getBlocks())
);
```

Do not pass `content`.

- [ ] **Step 7: Update detail result and response**

Modify `PostDetailResult` to replace `String content` with `List<PostContentBlockResult> blocks`.

Create `PostContentBlockResponse` mirroring the result:

```java
public class PostContentBlockResponse {
    private UUID id;
    private int index;
    private String type;
    private String text;
    private UUID assetId;
    private String language;
    private String caption;
    private String displayName;
    private Map<String, Object> metadata;
    private PostMedia media;

    public static PostContentBlockResponse from(PostContentBlockResult result) { ... }

    public static class PostMedia {
        private UUID assetId;
        private String mediaKind;
        private String lifecycle;
        private String videoState;
        private String fileName;
        private String contentType;
        private long contentLength;
        private String url;
        private String downloadUrl;
        private String posterUrl;
        private List<PostMediaViewResult.VideoSource> sources;
    }
}
```

Modify `PostDetailResponse`:

- remove `content`
- add `private List<PostContentBlockResponse> blocks;`
- `from` maps `view.blocks()`

- [ ] **Step 8: Update read service and assembler**

Modify `PostReadApplicationService#getPostDetail`:

- load post metadata as before
- load `List<PostContentBlock> blocks = postContentBlockRepository.listByPostId(postId)`
- load media assets for referenced asset ids
- pass blocks/assets to `PostDetailAssembler`

Modify `PostDetailAssembler` to assemble block results and media view results.

Rules:

- `image`: `url` and `downloadUrl` use asset `publicUrl`
- `file`: `downloadUrl` uses asset `publicUrl`
- `video`: `downloadUrl` uses asset `publicUrl`, `status` from asset `videoState`, empty `sources` until variants exist

- [ ] **Step 9: Add text projector and update summaries/search projection source**

Create `PostContentBlockTextProjector`:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.PostContentBlock;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class PostContentBlockTextProjector {
    public String preview(List<PostContentBlock> blocks, int maxChars) {
        String text = fullText(blocks);
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars));
    }

    public String fullText(List<PostContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return "";
        return blocks.stream()
                .map(this::blockText)
                .filter(StringUtils::hasText)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private String blockText(PostContentBlock block) {
        if (block == null) return "";
        return switch (String.valueOf(block.type()).toLowerCase()) {
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
```

Modify summary assembly so `PostReadApplicationService` fetches blocks for listed posts and sets a preview field:

- Add `String preview` to `PostSummaryResult`.
- Add `String preview` to `PostSummaryResponse`.
- Update `PostSummaryAssembler` to pass `preview`.
- Update `PostController` response conversion to return `preview`.
- Update frontend list rendering in Task 7 to display `p.preview` instead of `p.content`.
- Use `PostContentBlockTextProjector.preview(blocks)` as the single preview source.

- [ ] **Step 10: Update same-domain API adapters**

Create `content.api.model.PostContentBlockPayload` and change `PostPublishingActionApi` create/update signatures to accept `List<PostContentBlockPayload>`. Do not expose application command types through published `api.*` contracts.

Create `content.api.model.PostContentBlockView` and update `PostDetailView` to return `List<PostContentBlockView> blocks`.

Update `PostPublishingActionApiAdapter` to convert API payloads to application commands, and update `PostReadQueryApiAdapter` to convert application block results to `PostContentBlockView`.

- [ ] **Step 11: Update controller and read tests**

Modify `PostControllerUnitTest`:

- create request uses `request.setBlocks(List.of(paragraphBlock("body")))`
- verify create calls `postPublishingApplicationService.create(eq("idem-1"), argThat(command -> command.blocks().size() == 1))`
- detail assertion checks `getBlocks()` not `getContent()`
- update verifies new signature and blocks

Modify `PostReadApplicationServiceTest`:

- remove `post.setContent`
- mock block repository
- detail asserts blocks are returned

- [ ] **Step 12: Run focused backend API tests**

```bash
cd backend
mvn -q -pl :community-app -Dtest='PostPublishingApplicationServiceTest,PostReadApplicationServiceTest,PostControllerUnitTest,PostPublishingActionApiAdapterTest' test
```

Expected: PASS.

- [ ] **Step 13: Commit task 4**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/PostPublishingApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/api/PostPublishingActionApiAdapterTest.java
git commit -m "feat: publish and read block posts"
```

## Task 5: Search Projection Uses Block-Derived Text

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostScanView.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostScanService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/api/PostScanServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchReindexApplicationServiceTest.java`

- [ ] **Step 1: Update failing `PostScanServiceTest`**

Modify expected projection content from old post content to block-derived content:

```java
PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
when(blockRepository.listByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of(
        new PostContentBlock(uuid(30), postId, 0, "paragraph", "first paragraph", null, "", "", "", Map.of()),
        new PostContentBlock(uuid(31), postId, 1, "image", "", uuid(40), "", "chart caption", "", Map.of()),
        new PostContentBlock(uuid(32), postId, 2, "file", "", uuid(41), "", "", "logs.zip", Map.of())
)));

PostScanQueryApi service = new PostScanService(discussPostMapper, tagService, textCodec, blockRepository, new PostContentBlockTextProjector());

assertThat(response.items().get(0).content()).isEqualTo("first paragraph\nchart caption\nlogs.zip");
```

- [ ] **Step 2: Run failing projection tests**

```bash
cd backend
mvn -q -pl :community-app -Dtest='PostScanServiceTest,SearchPostProjectionApplicationServiceTest' test
```

Expected: compilation failure until `PostScanService` constructor and implementation are updated.

- [ ] **Step 3: Update `PostScanService`**

Inject `PostContentBlockRepository` and `PostContentBlockTextProjector`.

In `scanPosts`, load all blocks by post ids once:

```java
Map<UUID, List<PostContentBlock>> blocksByPostId = postContentBlockRepository.listByPostIds(postIds);
```

Pass block-derived full text to `PostProjectionView.content`.

In `getPostProjectionAllowDeleted`, load blocks for the single post and derive content.

- [ ] **Step 4: Remove `DiscussPost#getContent` usage from scan path**

Ensure `PostScanService` no longer calls `post.getContent()`.

Run:

```bash
rg -n "getContent\\(\\)|\\.content\\(\\)" backend/community-app/src/main/java/com/nowcoder/community/content backend/community-app/src/main/java/com/nowcoder/community/search
```

Expected: no post-body usage remains in content post paths. Comment content and search document content are still allowed.

- [ ] **Step 5: Run search/content projection tests**

```bash
cd backend
mvn -q -pl :community-app -Dtest='PostScanServiceTest,SearchPostProjectionApplicationServiceTest,SearchReindexApplicationServiceTest' test
```

Expected: PASS.

- [ ] **Step 6: Commit task 5**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostScanView.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostScanService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/api/PostScanServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/search/application
git commit -m "feat: project block posts for search"
```

## Task 6: Frontend API Clients For Blocks And Media Uploads

**Files:**
- Modify: `frontend/src/api/uploadSession.js`
- Modify: `frontend/src/api/services/postService.js`
- Modify: `frontend/src/api/services/postService.test.js`
- Create: `frontend/src/api/services/postMediaService.js`
- Create: `frontend/src/api/services/postMediaService.test.js`

- [ ] **Step 1: Update failing post service test**

Modify `frontend/src/api/services/postService.test.js` create/update test:

```js
it('createPost and updatePost should send block payloads without legacy content', async () => {
  const postId = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
  const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
  const blocks = [{ type: 'paragraph', text: 'world' }]
  mock = new MockAdapter(http)
  const seen = []
  mock.onPost('/api/posts').reply((config) => {
    seen.push(JSON.parse(config.data))
    return [200, { code: 0, message: '', data: { postId }, traceId: 'trace-create-post' }]
  })
  mock.onPut(`/api/posts/${postId}`).reply((config) => {
    seen.push(JSON.parse(config.data))
    return [200, { code: 0, message: '', data: null, traceId: 'trace-update-post' }]
  })

  await createPost({ title: 'hello', blocks, categoryId })
  await updatePost(postId, { title: 'hello', blocks, categoryId })

  expect(seen).toEqual([
    { title: 'hello', blocks, categoryId },
    { title: 'hello', blocks, categoryId }
  ])
  expect(seen[0]).not.toHaveProperty('content')
})
```

- [ ] **Step 2: Run failing post service test**

```bash
cd frontend
npm test -- src/api/services/postService.test.js
```

Expected: FAIL because current payload sends `content`.

- [ ] **Step 3: Update `postService.js`**

Change create/update signatures:

```js
export async function createPost({ title, blocks, categoryId, tags } = {}) {
  const payload = { title, blocks: normalizeBlocks(blocks) }
  ...
}

export async function updatePost(postId, { title, blocks, categoryId, tags } = {}) {
  const payload = { title, blocks: normalizeBlocks(blocks) }
  ...
}

function normalizeBlocks(blocks) {
  return (Array.isArray(blocks) ? blocks : []).map((b) => ({
    type: String(b?.type || '').trim(),
    text: b?.text == null ? '' : String(b.text),
    assetId: normalizeOpaqueId(b?.assetId) || undefined,
    language: b?.language == null ? '' : String(b.language),
    caption: b?.caption == null ? '' : String(b.caption),
    displayName: b?.displayName == null ? '' : String(b.displayName),
    metadata: b?.metadata && typeof b.metadata === 'object' ? b.metadata : undefined
  })).map((b) => Object.fromEntries(Object.entries(b).filter(([, v]) => v !== undefined)))
}
```

- [ ] **Step 4: Add media service test**

Create `frontend/src/api/services/postMediaService.test.js`:

```js
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { preparePostMediaUpload, uploadPostMediaFile } from './postMediaService'

describe('api/services/postMediaService', () => {
  let mock

  beforeEach(() => setActivePinia(createPinia()))
  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('prepares and executes post media upload sessions', async () => {
    mock = new MockAdapter(http)
    const assetId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock.onPost('/api/posts/media/upload-sessions').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({
        fileName: 'demo.png',
        contentType: 'image/png',
        contentLength: 4,
        mediaKind: 'IMAGE'
      })
      return [200, {
        code: 0,
        message: '',
        data: {
          assetId,
          uploadId: 'upload-1',
          upload: {
            url: `/api/posts/media/${assetId}/upload`,
            method: 'POST',
            fileField: 'file',
            fields: { uploadId: 'upload-1' },
            headers: {}
          },
          constraints: { maxBytes: 10, mimeTypes: ['image/png'] },
          expiresAt: '2026-05-09T00:00:00Z'
        },
        traceId: 'trace-session'
      }]
    })
    mock.onPost(`/api/posts/media/${assetId}/upload`).reply((config) => {
      expect(config.data).toBeInstanceOf(FormData)
      return [200, { code: 0, message: '', data: null, traceId: 'trace-upload' }]
    })

    const file = new File(['demo'], 'demo.png', { type: 'image/png' })
    const session = await preparePostMediaUpload({ file, mediaKind: 'IMAGE' })
    const uploaded = await uploadPostMediaFile({ session: session.data, file })

    expect(session.data.assetId).toBe(assetId)
    expect(uploaded.traceId).toBe('trace-upload')
  })
})
```

- [ ] **Step 5: Implement `postMediaService.js`**

Create:

```js
import http from '../http'
import { unwrapResultBody } from '../result'
import { executeUploadSession } from '../uploadSession'

export async function preparePostMediaUpload({ file, mediaKind, checksumSha256 = '' } = {}) {
  const payload = {
    fileName: String(file?.name || ''),
    contentType: String(file?.type || 'application/octet-stream'),
    contentLength: Number(file?.size || 0),
    mediaKind: String(mediaKind || inferMediaKind(file)).toUpperCase(),
    checksumSha256
  }
  const resp = await http.post('/api/posts/media/upload-sessions', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建帖子媒体上传会话')
  return { data: normalizePostMediaSession(data), traceId }
}

export async function uploadPostMediaFile({ session, file } = {}) {
  const { data, traceId } = await executeUploadSession({ http, session, file, operation: '上传帖子媒体' })
  return { data, traceId }
}

export function inferMediaKind(file) {
  const type = String(file?.type || '').toLowerCase()
  if (type.startsWith('image/')) return 'IMAGE'
  if (type.startsWith('video/')) return 'VIDEO'
  return 'FILE'
}

function normalizePostMediaSession(raw = {}) {
  return {
    ...raw,
    assetId: String(raw.assetId || ''),
    uploadId: String(raw.uploadId || '')
  }
}
```

- [ ] **Step 6: Run frontend API tests**

```bash
cd frontend
npm test -- src/api/services/postService.test.js src/api/services/postMediaService.test.js
```

Expected: PASS.

- [ ] **Step 7: Commit task 6**

```bash
git add frontend/src/api/services/postService.js \
  frontend/src/api/services/postService.test.js \
  frontend/src/api/services/postMediaService.js \
  frontend/src/api/services/postMediaService.test.js
git commit -m "feat: add block post frontend APIs"
```

## Task 7: Frontend Block Editor And Post Creation

**Files:**
- Create: `frontend/src/components/posts/PostMediaUploadBlock.vue`
- Create: `frontend/src/components/posts/PostBlockEditor.vue`
- Create: `frontend/src/components/posts/PostBlockEditor.test.js`
- Modify: `frontend/src/views/PostsView.vue`
- Modify: `frontend/src/views/posts/usePostsFeed.js`
- Modify: `frontend/src/views/posts/PostsView.css`
- Modify: `frontend/src/views/PostsView.test.js`

- [ ] **Step 1: Write failing block editor test**

Create `frontend/src/components/posts/PostBlockEditor.test.js`:

```js
// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PostBlockEditor from './PostBlockEditor.vue'

vi.mock('../../api/services/postMediaService', () => ({
  inferMediaKind: vi.fn(() => 'IMAGE'),
  preparePostMediaUpload: vi.fn().mockResolvedValue({
    data: {
      assetId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      uploadId: 'upload-1',
      upload: { url: '/upload', method: 'POST', fileField: 'file', fields: {}, headers: {} },
      constraints: { maxBytes: 10, mimeTypes: ['image/png'] }
    }
  }),
  uploadPostMediaFile: vi.fn().mockResolvedValue({ traceId: 'trace-upload' })
}))

describe('PostBlockEditor', () => {
  it('emits paragraph blocks and can add code blocks', async () => {
    const wrapper = mount(PostBlockEditor, {
      props: { modelValue: [{ type: 'paragraph', text: '' }] }
    })

    await wrapper.get('[data-test="block-text-0"]').setValue('hello')
    await wrapper.get('[data-test="add-code-block"]').trigger('click')

    const emitted = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(emitted[0]).toMatchObject({ type: 'paragraph', text: 'hello' })
    expect(emitted[1]).toMatchObject({ type: 'code' })
  })
})
```

- [ ] **Step 2: Run failing block editor test**

```bash
cd frontend
npm test -- src/components/posts/PostBlockEditor.test.js
```

Expected: FAIL because component does not exist.

- [ ] **Step 3: Implement `PostMediaUploadBlock.vue`**

Create a focused component that receives `block`, `index`, `disabled` and emits:

- `update:block`
- `remove`

Template should show:

- file picker for empty media block
- upload state text: `等待选择文件`, `上传中`, `上传完成`, `上传失败`
- caption/displayName input
- retry button on failure

Use `preparePostMediaUpload` and `uploadPostMediaFile` from `postMediaService`.

- [ ] **Step 4: Implement `PostBlockEditor.vue`**

Required props/emits:

```js
const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])
```

Minimum UI:

- renders each block in order
- paragraph/code use textarea/input
- image/video/file use `PostMediaUploadBlock`
- buttons:
  - `data-test="add-paragraph-block"`
  - `data-test="add-image-block"`
  - `data-test="add-video-block"`
  - `data-test="add-file-block"`
  - `data-test="add-code-block"`
- textarea data-test format: `block-text-${index}`

Normalize emitted block shape:

```js
{ type: 'paragraph', text: '' }
{ type: 'image', assetId: '', caption: '', uploadState: 'idle' }
{ type: 'video', assetId: '', caption: '', uploadState: 'idle' }
{ type: 'file', assetId: '', displayName: '', uploadState: 'idle' }
{ type: 'code', text: '', language: '' }
```

- [ ] **Step 5: Run block editor test**

```bash
cd frontend
npm test -- src/components/posts/PostBlockEditor.test.js
```

Expected: PASS.

- [ ] **Step 6: Update feed composer state**

Modify `usePostsFeed.js`:

- replace `newContent` with:

```js
const newBlocks = ref([{ type: 'paragraph', text: '' }])
```

- create validation helper:

```js
function publishableBlocks() {
  return (Array.isArray(newBlocks.value) ? newBlocks.value : [])
    .map((b) => ({ ...b }))
    .filter((b) => {
      if (['paragraph', 'code'].includes(b.type)) return String(b.text || '').trim()
      if (['image', 'video', 'file'].includes(b.type)) return normalizeOpaqueId(b.assetId)
      return false
    })
    .map((b) => {
      const clean = { type: b.type }
      if (b.text != null) clean.text = String(b.text)
      if (b.assetId) clean.assetId = normalizeOpaqueId(b.assetId)
      if (b.language) clean.language = String(b.language)
      if (b.caption) clean.caption = String(b.caption)
      if (b.displayName) clean.displayName = String(b.displayName)
      if (b.metadata) clean.metadata = b.metadata
      return clean
    })
}
```

- createPost checks `blocks.length > 0`
- calls `apiCreatePost({ title, blocks, categoryId, tags })`
- resets `newBlocks.value = [{ type: 'paragraph', text: '' }]`
- return `newBlocks` instead of `newContent`

- [ ] **Step 7: Update `PostsView.vue` composer**

Import `PostBlockEditor`.

Replace `UiTextarea v-model.trim="newContent"` with:

```vue
<PostBlockEditor
  v-model="newBlocks"
  class="posts-composer-block-editor"
  :disabled="creating"
/>
```

Update script destructuring from `newContent` to `newBlocks`.

- [ ] **Step 8: Update `PostsView.test.js`**

In mocks, ensure `createPost` receives blocks:

```js
import { createPost } from '../api/services/postService'

it('publishes block payload from composer', async () => {
  const wrapper = mountView()
  await openComposer(wrapper)
  await wrapper.get('input[name="post-title"]').setValue('hello')
  await wrapper.get('[data-test="block-text-0"]').setValue('body')
  await wrapper.get('.posts-composer-submit').trigger('click')
  await flushPromises()

  expect(createPost).toHaveBeenCalledWith(expect.objectContaining({
    title: 'hello',
    blocks: [expect.objectContaining({ type: 'paragraph', text: 'body' })]
  }))
})
```

- [ ] **Step 9: Add CSS for block editor**

Add restrained styles to `frontend/src/views/posts/PostsView.css`:

```css
.posts-composer-block-editor {
  min-height: 220px;
}

.post-block-editor {
  display: grid;
  gap: 12px;
}

.post-block-editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.post-block {
  border: 1px solid var(--border-1);
  border-radius: 8px;
  padding: 12px;
  background: var(--surface-1);
}
```

Use existing CSS variables/classes where available.

- [ ] **Step 10: Run frontend composer tests**

```bash
cd frontend
npm test -- src/components/posts/PostBlockEditor.test.js src/views/PostsView.test.js
```

Expected: PASS.

- [ ] **Step 11: Commit task 7**

```bash
git add frontend/src/components/posts/PostMediaUploadBlock.vue \
  frontend/src/components/posts/PostBlockEditor.vue \
  frontend/src/components/posts/PostBlockEditor.test.js \
  frontend/src/views/PostsView.vue \
  frontend/src/views/posts/usePostsFeed.js \
  frontend/src/views/posts/PostsView.css \
  frontend/src/views/PostsView.test.js
git commit -m "feat: add block post composer"
```

## Task 8: Frontend Post Detail Rendering And Block Editing

**Files:**
- Create: `frontend/src/components/posts/PostBlockRenderer.vue`
- Create: `frontend/src/components/posts/PostBlockRenderer.test.js`
- Modify: `frontend/src/views/PostDetailView.vue`
- Modify: `frontend/src/views/post-detail/usePostDetailLoader.js`
- Modify: `frontend/src/components/modals/EditContentModal.vue`
- Modify: `frontend/src/views/post-detail/PostDetailView.css`

- [ ] **Step 1: Write failing renderer test**

Create `PostBlockRenderer.test.js`:

```js
// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PostBlockRenderer from './PostBlockRenderer.vue'

describe('PostBlockRenderer', () => {
  it('renders paragraph image video pending and file blocks', () => {
    const wrapper = mount(PostBlockRenderer, {
      props: {
        blocks: [
          { type: 'paragraph', text: 'hello' },
          { type: 'image', caption: 'chart', media: { url: '/files/chart.png' } },
          { type: 'video', caption: 'demo', media: { videoState: 'PENDING_TRANSCODE', downloadUrl: '/files/demo.mp4', sources: [] } },
          { type: 'file', displayName: 'logs.zip', media: { downloadUrl: '/files/logs.zip' } },
          { type: 'code', text: 'const x = 1', language: 'js' }
        ]
      }
    })

    expect(wrapper.text()).toContain('hello')
    expect(wrapper.find('img[alt="chart"]').attributes('src')).toBe('/files/chart.png')
    expect(wrapper.text()).toContain('视频处理中')
    expect(wrapper.find('a[href="/files/logs.zip"]').text()).toContain('logs.zip')
    expect(wrapper.find('pre code').text()).toContain('const x = 1')
  })
})
```

- [ ] **Step 2: Run failing renderer test**

```bash
cd frontend
npm test -- src/components/posts/PostBlockRenderer.test.js
```

Expected: FAIL because renderer does not exist.

- [ ] **Step 3: Implement `PostBlockRenderer.vue`**

Render:

- paragraph: `<p>`
- image: `<figure><img :src="block.media?.url" :alt="block.caption || '图片'" /><figcaption>`
- video:
  - if sources non-empty: `<video controls :poster="posterUrl">`
  - else if `videoState === 'FAILED'`: failed state and download link
  - else: processing state and download link
- file: download row
- code: `<pre><code>`

No `v-html`; text only.

- [ ] **Step 4: Update detail page to use renderer**

Modify `PostDetailView.vue`:

- remove `UiMarkdown` import
- import `PostBlockRenderer`
- replace:

```vue
<UiMarkdown :content="post.content" />
```

with:

```vue
<PostBlockRenderer :blocks="post.blocks || []" />
```

- [ ] **Step 5: Update edit modal for post blocks**

Modify `EditContentModal.vue`:

- keep current textarea path for comments
- for `mode === 'post'`, render `PostBlockEditor v-model="blocks"`
- add prop:

```js
initialBlocks: { type: Array, default: () => [] }
```

- submit payload for post:

```js
emit('submit', { title: title.value, blocks: blocks.value })
```

- submit payload for comment remains `{ content }`

- [ ] **Step 6: Update detail loader edit state**

Modify `usePostDetailLoader.js`:

- add `editInitialBlocks = ref([])`
- `openEditPost()` sets:

```js
editInitialBlocks.value = Array.isArray(post.value.blocks) && post.value.blocks.length > 0
  ? post.value.blocks.map(toEditableBlock)
  : [{ type: 'paragraph', text: '' }]
```

- `submitEdit` for post calls:

```js
await apiUpdatePost(postId.value, {
  title: payload.title,
  blocks: payload.blocks,
  categoryId: post.value?.categoryId,
  tags: post.value?.tags || []
})
```

- comment edit remains content-based.

- [ ] **Step 7: Add detail CSS**

Add block renderer styles in `PostDetailView.css`:

```css
.post-block-renderer {
  display: grid;
  gap: 16px;
}

.post-render-block p {
  margin: 0;
  line-height: var(--line-loose);
}

.post-render-image img {
  max-width: 100%;
  border-radius: 8px;
  border: 1px solid var(--border-1);
}

.post-render-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid var(--border-1);
  border-radius: 8px;
  padding: 12px;
}
```

- [ ] **Step 8: Run renderer/detail tests**

```bash
cd frontend
npm test -- src/components/posts/PostBlockRenderer.test.js
```

Expected: PASS.

- [ ] **Step 9: Commit task 8**

```bash
git add frontend/src/components/posts/PostBlockRenderer.vue \
  frontend/src/components/posts/PostBlockRenderer.test.js \
  frontend/src/views/PostDetailView.vue \
  frontend/src/views/post-detail/usePostDetailLoader.js \
  frontend/src/components/modals/EditContentModal.vue \
  frontend/src/views/post-detail/PostDetailView.css
git commit -m "feat: render block post details"
```

## Task 9: Documentation And Verification

**Files:**
- Modify: `docs/handbook/business-logic/content.md`
- Modify: `docs/handbook/system-design.md`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`

- [ ] **Step 1: Update content handbook**

Modify `docs/handbook/business-logic/content.md`:

- Replace text-only publish steps with block publish steps.
- State that post body source of truth is `post_content_block`.
- State that `post_media_asset` owns content-domain media facts and OSS owns binary/object metadata.
- State that comments remain text-only.
- State there is no legacy post body compatibility.

- [ ] **Step 2: Update system design**

Modify `docs/handbook/system-design.md` OSS/content section:

- Add content post media upload session flow.
- Add note that video processing state exists but real transcoding is future work.

- [ ] **Step 3: Run backend focused tests**

```bash
cd backend
mvn -q -pl :community-app -Dtest='PostContentBlockPolicyTest,PostContentBlockMapperPersistenceTest,PostMediaAssetMapperPersistenceTest,PostMediaApplicationServiceTest,OssPostMediaStorageAdapterTest,PostMediaControllerUnitTest,PostPublishingApplicationServiceTest,PostReadApplicationServiceTest,PostControllerUnitTest,PostScanServiceTest,SearchPostProjectionApplicationServiceTest' test
```

Expected: PASS.

- [ ] **Step 4: Run ArchUnit tests**

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: PASS. If it fails because a controller/listener/job reaches across the application boundary, fix the code, not the test. If it fails because a new legitimate infrastructure package needs a documented rule, update the corresponding ArchUnit test and architecture docs in the same commit.

- [ ] **Step 5: Run frontend tests**

```bash
cd frontend
npm test -- src/api/services/postService.test.js src/api/services/postMediaService.test.js src/components/posts/PostBlockEditor.test.js src/components/posts/PostBlockRenderer.test.js src/views/PostsView.test.js
```

Expected: PASS.

- [ ] **Step 6: Run full frontend build**

```bash
cd frontend
npm run build
```

Expected: PASS.

- [ ] **Step 7: Run status and search for forbidden post content paths**

```bash
rg -n "setContent\\(|getContent\\(|request\\.getContent\\(|post\\.content|UiMarkdown :content=\" frontend/src/views/PostDetailView.vue frontend/src/views/PostsView.vue backend/community-app/src/main/java/com/nowcoder/community/content backend/community-app/src/main/resources/mapper/discusspost-mapper.xml
git status --short
```

Expected:

- No post body `content` usage remains in post create/update/detail paths.
- Comment content usage may remain elsewhere.
- Worktree only contains intended feature/doc changes.

- [ ] **Step 8: Commit documentation and verification fixes**

```bash
git add docs/handbook/business-logic/content.md docs/handbook/system-design.md backend/community-app/src/test/java/com/nowcoder/community/app/arch frontend backend
git commit -m "docs: document block post media publishing"
```

Only include `backend/community-app/src/test/java/com/nowcoder/community/app/arch` if ArchUnit changes were actually required.

## Final Verification

After all tasks are complete, run:

```bash
cd backend
mvn -q -pl :community-app -Dtest='Post*Test,SearchPostProjectionApplicationServiceTest,*ArchTest' test
```

Then run:

```bash
cd frontend
npm test -- src/api/services/postService.test.js src/api/services/postMediaService.test.js src/components/posts/PostBlockEditor.test.js src/components/posts/PostBlockRenderer.test.js src/views/PostsView.test.js
npm run build
```

Finally run:

```bash
git status --short
```

Expected: tests pass, frontend builds, and the worktree contains only committed changes or explicitly acknowledged local-only artifacts.
