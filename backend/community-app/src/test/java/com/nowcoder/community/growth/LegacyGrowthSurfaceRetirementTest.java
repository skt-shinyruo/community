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
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.AdminGrowthService");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.AdminRewardOpsService");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.RewardCatalogService");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.RewardOrderQueryService");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.RewardRedemptionService");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.CheckInService");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.TaskCenterService");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.TaskProgressTriggerService");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.TaskProgressProjectionService");
        assertClassIsRetired("com.nowcoder.community.growth.api.query.LegacyRewardAccountQueryApi");
        assertClassIsRetired("com.nowcoder.community.growth.api.model.LegacyRewardAccountView");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.RewardAccountService");
        assertClassIsRetired("com.nowcoder.community.wallet.infrastructure.api.WalletMigrationService");
        assertClassIsRetired("com.nowcoder.community.growth.api.action.GrowthGrantActionApi");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.UnifiedGrantService");
        assertClassIsRetired("com.nowcoder.community.growth.domain.model.RewardItem");
        assertClassIsRetired("com.nowcoder.community.growth.domain.model.RewardOrder");
        assertClassIsRetired("com.nowcoder.community.growth.domain.model.AdminRewardAdjustment");
        assertClassIsRetired("com.nowcoder.community.growth.domain.model.AdminRewardOrderAction");
        assertClassIsRetired("com.nowcoder.community.growth.domain.model.GrowthCheckIn");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.mapper.RewardAccountMapper");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.mapper.RewardLedgerMapper");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.mapper.RewardGrantRecordMapper");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.mapper.RewardItemMapper");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.mapper.RewardOrderMapper");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.mapper.AdminRewardAdjustmentMapper");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.mapper.AdminRewardOrderActionMapper");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.persistence.mapper.GrowthCheckInMapper");
        assertClassIsRetired("com.nowcoder.community.user.controller.LeaderboardController");
        assertClassIsRetired("com.nowcoder.community.user.infrastructure.api.LeaderboardService");
        assertClassIsRetired("com.nowcoder.community.user.infrastructure.api.PointsService");
        assertClassIsRetired("com.nowcoder.community.user.api.action.UserPointsActionApi");
        assertClassIsRetired("com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi");
        assertClassIsRetired("com.nowcoder.community.growth.api.model.GrowthCommentTaskProgressRequest");
        assertClassIsRetired("com.nowcoder.community.growth.api.model.GrowthLikeTaskProgressRequest");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.api.GrowthTaskProgressActionApiAdapter");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.event.PostTaskProgressKafkaOutboxEnqueuer");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.event.PostTaskProgressKafkaOutboxHandler");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.event.CommentTaskProgressOutboxEnqueuer");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.event.CommentTaskProgressKafkaOutboxHandler");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.event.LikeTaskProgressKafkaOutboxEnqueuer");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.event.LikeTaskProgressKafkaOutboxHandler");
        assertClassIsRetired("com.nowcoder.community.growth.application.TaskProgressOutboxDispatchApplicationService");
        assertClassIsRetired("com.nowcoder.community.growth.application.TaskProgressIntegrationEventDispatcher");
        assertClassIsRetired("com.nowcoder.community.growth.application.command.DispatchTaskProgressEventCommand");
        assertClassIsRetired("com.nowcoder.community.growth.application.command.TaskProgressDispatchKind");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.event.TaskProgressKafkaSenderAdapter");
        assertClassIsRetired("com.nowcoder.community.growth.infrastructure.event.TaskProgressKafkaListener");
    }

    @Test
    void legacyRewardShopMapperResourcesShouldBeRemoved() {
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/reward_item_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/reward_order_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/admin_reward_adjustment_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/admin_reward_order_action_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/growth_check_in_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/reward_account_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/reward_ledger_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/reward_grant_record_mapper.xml")).doesNotExist();
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
        assertThat(schema).doesNotContain("user_score_log");
        assertThat(schema).doesNotContain("reward_account");
        assertThat(schema).doesNotContain("reward_ledger");
        assertThat(schema).doesNotContain("reward_grant_record");
    }
}
