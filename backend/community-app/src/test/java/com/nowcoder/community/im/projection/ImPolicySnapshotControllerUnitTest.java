package com.nowcoder.community.im.projection;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ImPolicySnapshotControllerUnitTest {

    @Test
    void blockRelationsShouldTranslateUnsupportedModeToConflict() {
        ImPolicySnapshotService snapshotService = mock(ImPolicySnapshotService.class);
        doThrow(new UnsupportedOperationException("Redis-backed block projection snapshots are not supported"))
                .when(snapshotService).blockRelations(null, null, 10);

        ImPolicySnapshotController controller = new ImPolicySnapshotController(snapshotService);

        assertThatThrownBy(() -> controller.blockRelations(null, null, 10))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException statusException = (ResponseStatusException) ex;
                    assertThat(statusException.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(statusException.getReason()).contains("not supported");
                });
    }
}
