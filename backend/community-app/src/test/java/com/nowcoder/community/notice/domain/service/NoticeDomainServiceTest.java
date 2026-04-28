package com.nowcoder.community.notice.domain.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoticeDomainServiceTest {

    private final NoticeDomainService noticeDomainService = new NoticeDomainService();

    @Test
    void pageOrDefaultShouldClampNegativePageToZero() {
        assertThat(noticeDomainService.pageOrDefault(null)).isZero();
        assertThat(noticeDomainService.pageOrDefault(-1)).isZero();
        assertThat(noticeDomainService.pageOrDefault(3)).isEqualTo(3);
    }

    @Test
    void sizeOrDefaultShouldClampToSupportedRange() {
        assertThat(noticeDomainService.sizeOrDefault(null)).isEqualTo(10);
        assertThat(noticeDomainService.sizeOrDefault(0)).isEqualTo(1);
        assertThat(noticeDomainService.sizeOrDefault(100)).isEqualTo(50);
        assertThat(noticeDomainService.sizeOrDefault(20)).isEqualTo(20);
    }

    @Test
    void validateCreateShouldRejectMissingRequiredFields() {
        assertThatThrownBy(() -> noticeDomainService.validateCreate(null, "comment", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> noticeDomainService.validateCreate(UUID.randomUUID(), " ", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> noticeDomainService.validateCreate(UUID.randomUUID(), "comment", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateCreateShouldAcceptCompleteNotice() {
        noticeDomainService.validateCreate(UUID.randomUUID(), "comment", "{}");
    }
}
