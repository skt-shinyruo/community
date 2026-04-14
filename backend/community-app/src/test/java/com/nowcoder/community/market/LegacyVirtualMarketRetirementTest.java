package com.nowcoder.community.market;

import com.nowcoder.community.support.DeployCommunitySchema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyVirtualMarketRetirementTest {

    private static final Path MODULE_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path REPO_ROOT = MODULE_ROOT.getParent().getParent();

    @Test
    void legacyVirtualMarketClassesShouldNotRemainOnClasspath() {
        assertClassIsRetired("com.nowcoder.community.market.service.VirtualOrderService");
        assertClassIsRetired("com.nowcoder.community.market.service.VirtualDisputeService");
        assertClassIsRetired("com.nowcoder.community.market.service.VirtualListingService");
        assertClassIsRetired("com.nowcoder.community.market.service.VirtualInventoryService");
        assertClassIsRetired("com.nowcoder.community.market.service.VirtualMarketQueryService");
        assertClassIsRetired("com.nowcoder.community.infra.job.handlers.VirtualOrderAutoReleaseHandler");
        assertClassIsRetired("com.nowcoder.community.market.api.action.VirtualOrderAutoReleaseActionApi");
        assertClassIsRetired("com.nowcoder.community.market.api.model.VirtualOrderAutoReleaseResult");
    }

    @Test
    void legacyVirtualMarketMapperResourcesShouldBeRemoved() {
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/virtual_listing_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/virtual_inventory_unit_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/virtual_order_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/virtual_delivery_mapper.xml")).doesNotExist();
        assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/virtual_dispute_mapper.xml")).doesNotExist();
    }

    @Test
    void schemaShouldNotDefineLegacyVirtualTables() throws IOException {
        assertSchemaDoesNotContainVirtualTables(Files.readString(MODULE_ROOT.resolve("src/test/resources/schema.sql")));
        assertSchemaDoesNotContainVirtualTables(DeployCommunitySchema.read(REPO_ROOT));
    }

    private void assertClassIsRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private void assertSchemaDoesNotContainVirtualTables(String schema) {
        assertThat(schema).doesNotContain("virtual_listing");
        assertThat(schema).doesNotContain("virtual_inventory_unit");
        assertThat(schema).doesNotContain("virtual_order");
        assertThat(schema).doesNotContain("virtual_delivery");
        assertThat(schema).doesNotContain("virtual_dispute");
    }
}
