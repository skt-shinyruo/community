package com.nowcoder.community.growth;

import com.nowcoder.community.support.DeployCommunitySchema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyGrowthSurfaceRetirementTest {

    private static final Path MODULE_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path REPO_ROOT = MODULE_ROOT.getParent().getParent();

    @Test
    void legacyGrowthControllersAndServicesShouldNotRemainOnClasspath() {
        assertClassIsRetired("com.nowcoder.community.growth.controller.GrowthController");
        assertClassIsRetired("com.nowcoder.community.growth.controller.GrowthCenterController");
        assertClassIsRetired("com.nowcoder.community.growth.controller.RewardShopController");
        assertClassIsRetired("com.nowcoder.community.growth.controller.AdminGrowthController");
        assertClassIsRetired("com.nowcoder.community.growth.controller.AdminRewardOpsController");
        assertClassIsRetired("com.nowcoder.community.growth.security.GrowthSecurityRules");
        assertClassIsRetired("com.nowcoder.community.growth.service.AdminGrowthService");
        assertClassIsRetired("com.nowcoder.community.growth.service.AdminRewardOpsService");
        assertClassIsRetired("com.nowcoder.community.growth.service.RewardCatalogService");
        assertClassIsRetired("com.nowcoder.community.growth.service.RewardOrderQueryService");
        assertClassIsRetired("com.nowcoder.community.growth.service.RewardRedemptionService");
        assertClassIsRetired("com.nowcoder.community.growth.service.CheckInService");
        assertClassIsRetired("com.nowcoder.community.growth.service.TaskCenterService");
        assertClassIsRetired("com.nowcoder.community.growth.entity.RewardItem");
        assertClassIsRetired("com.nowcoder.community.growth.entity.RewardOrder");
        assertClassIsRetired("com.nowcoder.community.growth.entity.AdminRewardAdjustment");
        assertClassIsRetired("com.nowcoder.community.growth.entity.AdminRewardOrderAction");
        assertClassIsRetired("com.nowcoder.community.growth.entity.GrowthCheckIn");
        assertClassIsRetired("com.nowcoder.community.growth.mapper.RewardItemMapper");
        assertClassIsRetired("com.nowcoder.community.growth.mapper.RewardOrderMapper");
        assertClassIsRetired("com.nowcoder.community.growth.mapper.AdminRewardAdjustmentMapper");
        assertClassIsRetired("com.nowcoder.community.growth.mapper.AdminRewardOrderActionMapper");
        assertClassIsRetired("com.nowcoder.community.growth.mapper.GrowthCheckInMapper");
        assertClassIsRetired("com.nowcoder.community.user.controller.LeaderboardController");
        assertClassIsRetired("com.nowcoder.community.user.service.LeaderboardService");
        assertClassIsRetired("com.nowcoder.community.user.service.PointsService");
        assertClassIsRetired("com.nowcoder.community.user.api.action.UserPointsActionApi");
    }

    @Test
    void legacyRewardShopMapperResourcesShouldBeRemoved() {
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/reward_item_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/reward_order_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/admin_reward_adjustment_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/admin_reward_order_action_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/growth_check_in_mapper.xml")).doesNotExist();
    }

    @Test
    void schemaShouldNotDefineLegacyGrowthSurfaceTables() throws IOException {
        assertSchemaDoesNotContainRetiredTables(Files.readString(MODULE_ROOT.resolve("src/test/resources/schema.sql")));
        assertSchemaDoesNotContainRetiredTables(DeployCommunitySchema.read(REPO_ROOT));
    }

    private void assertClassIsRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private void assertSchemaDoesNotContainRetiredTables(String schema) {
        assertThat(schema).doesNotContain("reward_item");
        assertThat(schema).doesNotContain("reward_order");
        assertThat(schema).doesNotContain("admin_reward_adjustment");
        assertThat(schema).doesNotContain("admin_reward_order_action");
        assertThat(schema).doesNotContain("growth_check_in");
    }
}
