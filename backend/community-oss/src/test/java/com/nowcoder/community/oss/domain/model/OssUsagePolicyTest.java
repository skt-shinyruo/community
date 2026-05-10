package com.nowcoder.community.oss.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssUsagePolicyTest {

    @Test
    void usagePolicyShouldNormalizeInputsAndEnforceUploadConstraints() {
        OssUsagePolicy policy = new OssUsagePolicy(
                " USER_AVATAR ",
                OssVisibility.PUBLIC,
                8,
                Set.of(" IMAGE/PNG ", "image/jpeg"),
                true,
                false,
                true,
                60,
                120,
                " public, max-age=60 ",
                " no-store ",
                -1,
                -5
        );

        assertThat(policy.usage()).isEqualTo("USER_AVATAR");
        assertThat(policy.defaultVisibility()).isEqualTo(OssVisibility.PUBLIC);
        assertThat(policy.allowedMimeTypes()).containsExactlyInAnyOrder("image/png", "image/jpeg");
        assertThat(policy.retentionDays()).isZero();
        assertThat(policy.deleteGraceDays()).isZero();

        policy.validateUpload("image/png", 8, "sha256");
        assertThatThrownBy(() -> policy.validateUpload("image/png", 9, "sha256"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBytes");
        assertThatThrownBy(() -> policy.validateUpload("text/plain", 1, "sha256"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content type");
        assertThatThrownBy(() -> policy.validateUpload("image/png", 1, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void usagePolicyShouldAllowPrivateDriveDefaults() {
        OssUsagePolicy policy = new OssUsagePolicy(
                "DRIVE_FILE",
                OssVisibility.PRIVATE,
                10737418240L,
                Set.of(),
                false,
                false,
                true,
                300,
                900,
                "",
                "no-store",
                0,
                7
        );

        assertThat(policy.defaultVisibility()).isEqualTo(OssVisibility.PRIVATE);
    }
}
