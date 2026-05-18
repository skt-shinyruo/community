package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.application.ContentEntityResolutionApplicationService;
import com.nowcoder.community.content.application.result.ResolvedContentResult;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentEntityQueryServiceTest {

    @Test
    void resolveShouldDelegateToApplicationService() {
        ContentEntityResolutionApplicationService applicationService = mock(ContentEntityResolutionApplicationService.class);
        ResolvedContentResult result = new ResolvedContentResult(uuid(7), uuid(101));
        when(applicationService.resolve(EntityTypes.POST, uuid(101))).thenReturn(result);

        ContentEntityQueryService service = new ContentEntityQueryService(applicationService);

        ResolvedContentRef resolved = service.resolve(EntityTypes.POST, uuid(101));

        assertThat(resolved.entityUserId()).isEqualTo(uuid(7));
        assertThat(resolved.postId()).isEqualTo(uuid(101));
        verify(applicationService).resolve(EntityTypes.POST, uuid(101));
    }
}
