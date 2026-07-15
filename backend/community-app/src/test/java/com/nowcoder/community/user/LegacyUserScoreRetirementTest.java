package com.nowcoder.community.user;

import com.nowcoder.community.support.DeployCommunitySchema;
import com.nowcoder.community.profile.controller.dto.UserProfileResponse;
import com.nowcoder.community.user.domain.service.UserReadDomainService;
import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyUserScoreRetirementTest {

    private static final Path MODULE_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path REPO_ROOT = MODULE_ROOT.getParent().getParent();

    @Test
    void userProfileShouldNotExposeLegacyScoreOrScoreLevel() {
        assertThatThrownBy(() -> UserProfileResponse.class.getDeclaredMethod("getScore"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> UserProfileResponse.class.getDeclaredMethod("setScore", int.class))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> UserProfileResponse.class.getDeclaredMethod("getLevel"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> UserProfileResponse.class.getDeclaredMethod("setLevel", int.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void userDomainShouldNotKeepLegacyScoreHelpers() {
        assertThatThrownBy(() -> UserReadDomainService.class.getDeclaredMethod("levelForScore", int.class))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> UserMapper.class.getDeclaredMethod("addScore", java.util.UUID.class, int.class))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> UserMapper.class.getDeclaredMethod("selectTopByScore", int.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void userRewardBridgeShouldNotKeepLegacyPointsNames() {
        assertClassIsRetired("com.nowcoder.community.user.application.UserPointsApplicationService");
        assertClassIsRetired("com.nowcoder.community.user.api.action.UserPointsAwardActionApi");
        assertClassIsRetired("com.nowcoder.community.user.api.model.UserCommentPointsAwardRequest");
        assertClassIsRetired("com.nowcoder.community.user.api.model.UserLikePointsAwardRequest");
        assertClassIsRetired("com.nowcoder.community.user.infrastructure.api.UserPointsAwardApiAdapter");
    }

    @Test
    void schemasShouldNotDefineLegacyUserScoreColumn() throws IOException {
        assertSchemaDoesNotContainLegacyUserScore(Files.readString(MODULE_ROOT.resolve("src/test/resources/schema.sql")));
        assertSchemaDoesNotContainLegacyUserScore(DeployCommunitySchema.read(REPO_ROOT));
    }

    private void assertSchemaDoesNotContainLegacyUserScore(String schema) {
        assertThat(schema).doesNotContain("score int");
        assertThat(schema).doesNotContain("create_time, score");
    }

    private void assertClassIsRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
